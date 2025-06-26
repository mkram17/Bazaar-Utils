package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.utils.Util;
import net.hypixel.api.HypixelAPI;
import net.hypixel.api.apache.ApacheHttpClient;
import net.hypixel.api.reply.AbstractReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class APIUtils {

    private static final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);

    public static String getApiKey() {
        String apiKey = System.getenv("HYPIXEL_API_KEY");
        if (apiKey != null) {
            return apiKey;
        }
        //fake api key, but wont be used so it doesnt matter
        return "11111111-2222-3333-4444-555555555555";
    }

    public static final HypixelAPI API;
    public static final UUID uuid;

    static {
        uuid = UUID.fromString(getApiKey());
        API = new HypixelAPI(new ApacheHttpClient(uuid));
    }

    public static CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaarWithRetry() {
        return getSkyBlockBazaarWithRetry(0);
    }
    
    private static CompletableFuture<SkyBlockBazaarReply> getSkyBlockBazaarWithRetry(int attempt) {
        final int MAX_RETRIES = 3;
        final long RETRY_DELAY_MS = 2000;
        
        return API.getSkyBlockBazaar().handle((result, throwable) -> {
            if (throwable != null && attempt < MAX_RETRIES) {
                if (isRetryableError(throwable)) {
                    int nextAttempt = attempt + 1;
                    long delay = RETRY_DELAY_MS * nextAttempt;
                    
                    Util.notifyAll("API connection error on attempt " + nextAttempt + "/" + (MAX_RETRIES + 1) + 
                                 ", retrying in " + (delay / 1000) + " seconds...", 
                                 Util.notificationTypes.BAZAARDATA);
                    
                    CompletableFuture<SkyBlockBazaarReply> delayed = new CompletableFuture<>();
                    retryExecutor.schedule(() -> {
                        getSkyBlockBazaarWithRetry(nextAttempt).whenComplete((retryResult, retryThrowable) -> {
                            if (retryThrowable != null) {
                                delayed.completeExceptionally(retryThrowable);
                            } else {
                                delayed.complete(retryResult);
                            }
                        });
                    }, delay, TimeUnit.MILLISECONDS);
                    
                    try {
                        return delayed.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            
            if (throwable != null) {
                throw new RuntimeException(throwable);
            }
            return result;
        });
    }
    
    private static boolean isRetryableError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getSimpleName();
            String message = current.getMessage();
            
            if (current instanceof org.apache.http.ConnectionClosedException ||
                current instanceof java.net.SocketTimeoutException ||
                current instanceof java.net.ConnectException ||
                current instanceof java.io.EOFException) {
                return true;
            }
            
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                if (lowerMessage.contains("connection closed") ||
                    lowerMessage.contains("premature end") ||
                    lowerMessage.contains("chunk coded message body") ||
                    lowerMessage.contains("unexpected end of zlib input stream") ||
                    lowerMessage.contains("connection reset") ||
                    lowerMessage.contains("read timed out") ||
                    lowerMessage.contains("connect timed out")) {
                    return true;
                }
            }
            
            if (className.contains("IOException") ||
                className.contains("SocketException") ||
                className.contains("ConnectException") ||
                className.contains("TimeoutException")) {
                return true;
            }
            
            current = current.getCause();
        }
        
        return false;
    }

    public static <T extends AbstractReply> BiConsumer<T, Throwable> getTestConsumer() {
        return (result, throwable) -> {
            if (throwable != null) {
                Util.notifyError("Error while getting data from Hypixel API", throwable);
                return;
            }

//            System.out.println(result);

            System.exit(0);
        };
    }
}
