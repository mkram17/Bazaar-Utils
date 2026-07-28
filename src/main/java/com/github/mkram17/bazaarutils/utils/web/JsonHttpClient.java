package com.github.mkram17.bazaarutils.utils.web;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.JsonParseException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal async JSON-over-HTTP helper, built on the JDK's {@link HttpClient} so the mod gains no
 * new dependency. {@link com.github.mkram17.bazaarutils.utils.APIUtil} is a Hypixel API wrapper,
 * not a general transport, so it cannot serve this.
 *
 * <p>Three properties matter for a Minecraft client mod, and all three are enforced here rather
 * than left to callers:</p>
 * <ul>
 *   <li><strong>Never on the render thread.</strong> Requests run on this class's own daemon pool
 *       and every method returns a {@link CompletableFuture}.</li>
 *   <li><strong>Always bounded.</strong> Connect and per-request timeouts, plus a capped
 *       retry/backoff for transient failures only — never for a 4xx, which will fail identically
 *       however many times it is sent.</li>
 *   <li><strong>Quiet on failure.</strong> Nothing here touches chat. Failures surface as a
 *       {@link Response} or a failed future and are logged; deciding what (if anything) the player
 *       should see is the caller's job.</li>
 * </ul>
 */
public final class JsonHttpClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 500;

    private static final String USER_AGENT =
            BazaarUtils.MOD_ID + "/" + BazaarUtils.MOD_CONTAINER.getMetadata().getVersion().getFriendlyString();

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, BazaarUtils.MOD_NAME + " HTTP " + counter.incrementAndGet());
            // Daemon: a request in flight must never keep the game process alive at shutdown.
            thread.setDaemon(true);
            return thread;
        }
    };

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2, THREAD_FACTORY);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(EXECUTOR)
            .build();

    private JsonHttpClient() {}

    /**
     * The pool backing every request here. Callers with their own off-thread work in the same
     * flow (the {@code joinServer} handshake, for one) should run it on this executor rather than
     * on {@link BazaarUtils#BUExecutorService}, whose single thread is shared with update checks.
     */
    public static Executor executor() {
        return EXECUTOR;
    }

    /**
     * One HTTP exchange that completed, whatever its status. A non-2xx is a {@code Response}, not
     * a failed future — the endpoints here answer with meaningful status codes (402 for a lapsed
     * subscription, 401 for a revoked token) that callers must be able to branch on.
     */
    public record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        /**
         * Deserializes the body into {@code type}, or empty when there is no body or it does not
         * parse — a proxy or gateway answering with HTML is an ordinary failure, not a crash.
         *
         * <p>Fields the server omitted are left null, so a present result does not mean every
         * field is populated.</p>
         */
        public <T> Optional<T> as(Class<T> type) {
            if (body == null || body.isBlank()) return Optional.empty();

            try {
                return Optional.ofNullable(WebJson.GSON.fromJson(body, type));
            } catch (JsonParseException exception) {
                return Optional.empty();
            }
        }

        /**
         * The API's {@code error} field. Every failure path on the website answers with one, and
         * its text is written to be shown to a player, so prefer it over a message invented here.
         */
        public Optional<String> errorMessage() {
            return as(ApiError.class).map(ApiError::error).filter(message -> !message.isBlank());
        }

        /**
         * The API's machine-readable {@code reason} for a failure, where it sends one.
         *
         * <p>Separate from {@link #errorMessage()} because the two answer different questions.
         * The message is what to show; the reason is what to <em>do</em> — a lapsed subscription
         * and a revoked token both arrive as a failed sync, but only one of them means the stored
         * token should be thrown away. Absent from older server versions, so every caller needs a
         * path for empty.</p>
         */
        public Optional<String> errorReason() {
            return as(ApiError.class).map(ApiError::reason).filter(reason -> !reason.isBlank());
        }

        private record ApiError(String error, String reason) {}
    }

    /**
     * POSTs a JSON body and completes with the response.
     *
     * <p>Retries only what a retry can fix: connection failures, timeouts, 429, and 5xx. A 4xx is
     * returned as-is on the first attempt.</p>
     *
     * @param uri     endpoint to call
     * @param body    already-serialized JSON
     * @param headers extra headers, e.g. {@code Authorization}
     */
    public static CompletableFuture<Response> postJson(URI uri, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body));

        headers.forEach(builder::header);

        HttpRequest request = builder.build();

        return send(request, 1);
    }

    /**
     * One attempt, and exactly one retry decision for it. Success and failure are handled in a
     * single {@code handle} rather than a {@code thenCompose}/{@code exceptionallyCompose} pair —
     * with two stages, a retry chain that ends in a transport failure would be seen a second time
     * by the exception stage and retried again, quietly multiplying the attempt cap.
     */
    private static CompletableFuture<Response> send(HttpRequest request, int attempt) {
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((httpResponse, throwable) -> {
                    if (throwable != null) {
                        // An IOException here is a connection or timeout failure; both are worth
                        // another try. Anything else is a bug and should surface immediately.
                        if (attempt >= MAX_ATTEMPTS || !isTransport(throwable)) {
                            return CompletableFuture.<Response>failedFuture(throwable);
                        }

                        Util.logMessage("%s failed (%s), retrying (attempt %d/%d)"
                                .formatted(request.uri(), throwable.getMessage(), attempt + 1, MAX_ATTEMPTS));

                        return retry(request, attempt);
                    }

                    Response response = new Response(httpResponse.statusCode(), httpResponse.body());

                    if (!isRetryable(response.status()) || attempt >= MAX_ATTEMPTS) {
                        return CompletableFuture.completedFuture(response);
                    }

                    Util.logMessage("%s returned %d, retrying (attempt %d/%d)"
                            .formatted(request.uri(), response.status(), attempt + 1, MAX_ATTEMPTS));

                    return retry(request, attempt);
                })
                .thenCompose(future -> future);
    }

    private static CompletableFuture<Response> retry(HttpRequest request, int attempt) {
        long delayMillis = INITIAL_BACKOFF_MILLIS << (attempt - 1);

        CompletableFuture<Response> next = new CompletableFuture<>();

        EXECUTOR.schedule(
                () -> send(request, attempt + 1).whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        next.completeExceptionally(throwable);
                    } else {
                        next.complete(response);
                    }
                }),
                delayMillis,
                java.util.concurrent.TimeUnit.MILLISECONDS
        );

        return next;
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    /**
     * Whether another attempt could plausibly succeed.
     *
     * <p>Not every {@link IOException} is worth repeating. A TLS handshake against a plaintext
     * port and an unresolvable hostname are configuration errors: they will fail identically on
     * every attempt, so retrying only delays the report. A refused connection or a timeout can
     * genuinely be transient (a dev server still starting up), so those still get their retries.</p>
     */
    private static boolean isTransport(Throwable throwable) {
        boolean sawIoFailure = false;

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SSLException || current instanceof UnknownHostException) return false;
            if (current instanceof IOException) sawIoFailure = true;
            if (current.getCause() == current) break;
        }

        return sawIoFailure;
    }

    /**
     * A short, specific explanation of a transport failure, when one can be identified.
     *
     * <p>"Check your connection" is the wrong advice for most of these — the connection is usually
     * fine and the URL is wrong.</p>
     */
    public static Optional<String> describeTransportFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SSLException) {
                return Optional.of("the URL starts with https but the server answered in plain HTTP — check the scheme");
            }

            if (current instanceof UnknownHostException) {
                return Optional.of("that hostname does not resolve");
            }

            if (current instanceof ConnectException) {
                return Optional.of("nothing is listening on that host and port");
            }

            if (current instanceof HttpTimeoutException) {
                return Optional.of("the request timed out");
            }

            if (current.getCause() == current) break;
        }

        return Optional.empty();
    }
}
