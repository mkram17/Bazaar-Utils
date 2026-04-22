package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.hidden.MetadataConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

//TODO move config to config/bazaarutils directory and rename to "config". See how REI does this.
public class ResourceManager {
    private static final BazaarLogger LOG = BazaarLogger.of(ResourceManager.class);

    private static final Path MOD_CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(BazaarUtils.MOD_ID);
    private static final Path LOCAL_RESOURCES_PATH = MOD_CONFIG_DIR.resolve("bazaar-resources.json");
    private static final Identifier BUNDLED_RESOURCES_ID = Identifier.fromNamespaceAndPath(BazaarUtils.MOD_ID, "bazaar-resources.json");
    private static final String GITHUB_API_URL = "https://api.github.com/repos/mkram17/Skyblock-Bazaar-Conversions/contents/conversionupdating/bazaar-conversions.json?ref=main";
    /* Cached conversions: lowercase name -> productInfo */
    @Getter
    private static volatile Map<String, String> nameToProductIdCache = Map.of();
    /* Cached known values to not have to call .containsValue when testing for product ids */
    @Getter
    private static volatile Map<String, String> productIdtoNameCache = Map.of();

    @Setter
    private static volatile boolean conversionsLoaded = false;

    public static void initialize() {
        LOG.info("ResourceManager initialising — local path={}", LOCAL_RESOURCES_PATH);

        CompletableFuture.runAsync(() -> {
            try {
                if (!Files.exists(MOD_CONFIG_DIR)) {
                    Files.createDirectories(MOD_CONFIG_DIR);
                }
                copyDefaultResourcesIfMissing();
                checkForUpdates(false); // Automatic check on startup
                ensureConversionsLoaded();
            } catch (IOException exception) {
                PlayerLogger.sendError("Failed to initialize resource manager", exception);
            }
        });
    }

    private static void copyDefaultResourcesIfMissing() throws IOException {
        if (Files.exists(LOCAL_RESOURCES_PATH)) {
            return;
        }

        LOG.info("Local resources file not found — copying from bundled resources");

        Optional<Resource> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(BUNDLED_RESOURCES_ID);
        if (resourceOptional.isPresent()) {
            try (InputStream inputStream = resourceOptional.get().open()) {
                Files.copy(inputStream, LOCAL_RESOURCES_PATH);
                // don't know the SHA of the bundled file, so stays null to force an update check.
                MetadataConfig.RESOURCES_SHA = "";
                ConfigUtil.scheduleConfigSave();
            }
        } else {
            PlayerLogger.sendError("Could not find bundled bazaar-resources.json — mod may be corrupted", new Throwable());
        }
    }

    public static void checkForUpdates(boolean manual) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URI(GITHUB_API_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (connection.getResponseCode() != 200) {
                    if (manual) PlayerLogger.sendError("Failed to check for resource updates (HTTP " + connection.getResponseCode() + ")", new Throwable());

                    LOG.warn("GitHub API responded with code {} — skipping update check", connection.getResponseCode());

                    return;
                }

                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String responseBody = scanner.useDelimiter("\\A").next();
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    String latestSha = jsonObject.get("sha").getAsString();
                    String downloadUrl = jsonObject.get("download_url").getAsString();

                    if (!latestSha.equals(MetadataConfig.RESOURCES_SHA)) {
                        LOG.info("Resource update available — downloading (sha={})", latestSha);
                        if (manual) PlayerLogger.send("New resources found, downloading...");
                        downloadLatestResources(downloadUrl, latestSha);
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

    private static void downloadLatestResources(String downloadUrl, String latestSha) {
        Path tempPath = LOCAL_RESOURCES_PATH.resolveSibling("bazaar-resources.json.tmp");
        try (InputStream in = new URI(downloadUrl).toURL().openStream()) {
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, LOCAL_RESOURCES_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            MetadataConfig.RESOURCES_SHA = latestSha;
            ConfigUtil.scheduleConfigSave();
            ResourceManager.setConversionsLoaded(false);
            ensureConversionsLoaded();
            LOG.info("Resources updated successfully — sha={}", latestSha);
            PlayerLogger.send("Bazaar resources updated successfully.");
        } catch (Exception exception) {
            PlayerLogger.sendError("Failed to download resource update", exception);

            try {
                Files.deleteIfExists(tempPath); // Clean up the temporary file on failure
            } catch (IOException ex) {
                LOG.warn("Failed to delete temporary resource file — path={}", tempPath, ex);
            }
        }
    }

    public static JsonObject getResourceJson() {
        try {
            String content = Files.readString(LOCAL_RESOURCES_PATH);
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (IOException e) {
            LOG.warn("Could not read local bazaar-resources.json — falling back to bundled", e);
            // Fallback to bundled resources if local read fails
            try {
                Optional<Resource> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(BUNDLED_RESOURCES_ID);
                if (resourceOptional.isPresent()) {
                    try (InputStream inputStream = resourceOptional.get().open()) {
                        String content = new String(inputStream.readAllBytes());
                        return JsonParser.parseString(content).getAsJsonObject();
                    }
                }
            } catch (IOException ex) {
                PlayerLogger.sendError("Failed to read resource file and bundled fallback also failed", ex);
            }
        }
        return new JsonObject(); //empty (shouldnt happen)
    }

    @RunOnInit
    public static void onClientStart(){
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ResourceManager.initialize();
        });
    }

    /**
     * Cached conversion load. Thread-safe (single pass).
     */
    public static void ensureConversionsLoaded() {
        if (conversionsLoaded) {
            return;
        }

        // Double-checked guard avoids repeated JSON parsing on the hot path.
        synchronized (ResourceManager.class) {
            if (conversionsLoaded) {
                return;
            }

            try {
                Map<String, String> nameToId = new HashMap<>();
                Map<String, String> idToName = new HashMap<>();

                var resources = getResourceJson();
                var conversions = resources.getAsJsonObject();

                for (String key : conversions.keySet()) {
                    String value = conversions.get(key).getAsString();
                    if (value != null) {
                        nameToId.put(value.toLowerCase(Locale.ROOT), key);
                        idToName.put(key, value);
                    }
                }

                nameToProductIdCache = Collections.unmodifiableMap(nameToId);
                productIdtoNameCache = Collections.unmodifiableMap(idToName);
                conversionsLoaded = true;

                LOG.info("Resource cache loaded — {} entries", nameToProductIdCache.size());
            } catch (Exception exception) {
                PlayerLogger.sendError("Failed to load resource cache — order tracking will not work. Try /bu updateresources or restart the game.", exception);

                nameToProductIdCache = Map.of();
                conversionsLoaded = true;
            }
        }
    }
}