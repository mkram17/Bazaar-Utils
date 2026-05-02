package com.github.mkram17.bazaarutils.utils.resources;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the lifecycle of the locally-cached bazaar-resources.json file.
 */
@PreInitModule
public final class BazaarConversionsUpdater extends BUListener {
    private static final BazaarLogger LOG = BazaarLogger.of(BazaarConversionsUpdater.class);

    private static final Path MOD_CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve(BazaarUtils.MOD_ID);

    private static final Path LOCAL_RESOURCES_PATH =
            MOD_CONFIG_DIR.resolve("bazaar-resources.json");
    private static final Identifier BUNDLED_RESOURCES_ID =
            Identifier.fromNamespaceAndPath(BazaarUtils.MOD_ID, "bazaar-resources.json");
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/mkram17/Skyblock-Bazaar-Conversions/contents/conversionupdating/bazaar-conversions.json?ref=main";

    public BazaarConversionsUpdater() {}

    @Override
    protected void registerFabricEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> initialize());
    }

    private static void initialize() {
        LOG.info("BazaarConversionsUpdater initialising — local path={}", LOCAL_RESOURCES_PATH);

        CompletableFuture.runAsync(() -> {
            try {
                if (!Files.exists(MOD_CONFIG_DIR)) {
                    Files.createDirectories(MOD_CONFIG_DIR);
                }
                copyBundledIfMissing();
                checkForUpdates(false);
                BazaarConversions.ensureLoaded();
            } catch (IOException exception) {
                PlayerLogger.sendError("Failed to initialize resource manager", exception);
            }
        });
    }

    /**
     * Checks GitHub for a newer version of the resources file.
     *
     * @param manual {@code true} when triggered by the player (e.g. {@code /bu updateresources});
     *               shows chat feedback and errors. {@code false} for the silent startup check.
     */
    public static void checkForUpdates(boolean manual) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URI(GITHUB_API_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (connection.getResponseCode() != 200) {
                    LOG.warn("GitHub API responded with code {} — skipping update check", connection.getResponseCode());
                    if (manual) PlayerLogger.sendError("Failed to check for resource updates (HTTP " + connection.getResponseCode() + ")", new Throwable());

                    return;
                }

                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String responseBody = scanner.useDelimiter("\\A").next();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    String latestSha = json.get("sha").getAsString();
                    String downloadUrl = json.get("download_url").getAsString();

                    if (!latestSha.equals(MetadataConfig.RESOURCES_SHA)) {
                        LOG.info("Resource update available — downloading (sha={})", latestSha);
                        if (manual) PlayerLogger.send("New resources found, downloading...");

                        downloadAndReplace(downloadUrl, latestSha);
                    } else {
                        LOG.info("Resources up to date (sha={})", latestSha);
                        if (manual) PlayerLogger.send("Resources are already up to date.");
                    }
                }
            } catch (Exception exception) {
                LOG.warn("Failed to check for resource updates", exception);
                if (manual) PlayerLogger.sendError("Failed to check for resource updates", exception);
            }
        });
    }

    private static void copyBundledIfMissing() throws IOException {
        if (Files.exists(LOCAL_RESOURCES_PATH)) return;
        LOG.info("Local resources file not found — copying from bundled resources");

        Optional<Resource> resource = Minecraft.getInstance()
                .getResourceManager()
                .getResource(BUNDLED_RESOURCES_ID);

        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                Files.copy(in, LOCAL_RESOURCES_PATH);
                // SHA unknown for bundled file — force update check on next run
                MetadataConfig.RESOURCES_SHA = "";
                ConfigUtil.scheduleConfigSave();
            }
        } else {
            PlayerLogger.sendError("Could not find bundled bazaar-resources.json — mod may be corrupted", new Throwable());
        }
    }

    private static void downloadAndReplace(String downloadUrl, String latestSha) {
        Path tempPath = LOCAL_RESOURCES_PATH.resolveSibling("bazaar-resources.json.tmp");

        try (InputStream in = new URI(downloadUrl).toURL().openStream()) {
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, LOCAL_RESOURCES_PATH,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            MetadataConfig.RESOURCES_SHA = latestSha;
            ConfigUtil.scheduleConfigSave();

            BazaarConversions.invalidate();
            BazaarConversions.ensureLoaded();

            LOG.info("Resources updated successfully — sha={}", latestSha);
            PlayerLogger.send("Bazaar resources updated successfully.");
        } catch (Exception exception) {
            PlayerLogger.sendError("Failed to download resource update", exception);

            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ex) {
                LOG.warn("Failed to delete temporary resource file — path={}", tempPath, ex);
            }
        }
    }

    /**
     * Reads the local resources file, falling back to the bundled copy on failure.
     *
     * @return parsed {@link JsonObject}, never null (empty object as last resort)
     */
    static JsonObject readResourceJson() {
        try {
            return JsonParser.parseString(Files.readString(LOCAL_RESOURCES_PATH)).getAsJsonObject();
        } catch (IOException exception) {
            LOG.warn("Could not read local bazaar-resources.json — falling back to bundled", exception);
        }

        try {
            Optional<Resource> resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(BUNDLED_RESOURCES_ID);

            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    return JsonParser.parseString(new String(in.readAllBytes())).getAsJsonObject();
                }
            }
        } catch (IOException exception) {
            PlayerLogger.sendError("Failed to read resource file and bundled fallback also failed", exception);
        }

        return new JsonObject();
    }
}