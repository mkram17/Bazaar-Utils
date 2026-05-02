package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.google.gson.*;
import de.hysky.skyblocker.config.SkyblockerConfig;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BUCompatibilityHelper {
    private static final BazaarLogger LOG = BazaarLogger.of(BUCompatibilityHelper.class);

    private static final String REI_MOD_ID = "roughlyenoughitems";
    public static final String SKYBLOCKER_MOD_ID = "skyblocker";
    public static final String FIRMAMENT_MODID = "firmament";

    private static final String REI_CONFIG_FILENAME = "roughlyenoughitems/config.json5";
    private static final String REI_CONFIG_SECTION = "appearance";
    private static final String REI_CONFIG_FIELD = "horizontalEntriesBoundariesColumns";
    private static final int REI_COLUMNS_TARGET_VALUE = 16;

    private static final Gson GSON_WRITER = new GsonBuilder().setPrettyPrinting().create();

    public static void initializePatches() {
        if (FabricLoader.getInstance().isModLoaded(REI_MOD_ID)) {
            LOG.info("REI detected — applying config patch");

            modifyReiConfigWithGson();
        } else {
            LOG.info("REI not present — skipping REI config patch");
        }
    }

    private static void modifyReiConfigWithGson() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path reiConfigFile = configDir.resolve(REI_CONFIG_FILENAME);

        LOG.info("REI config path: {}", reiConfigFile.toAbsolutePath());

        if (!Files.exists(reiConfigFile)) {
            // User-visible + logged: this is actionable for a bug report
            PlayerLogger.sendError("REI config not found at: " + reiConfigFile, null);

            return;
        }

        JsonObject rootObject;

        try (BufferedReader reader = Files.newBufferedReader(reiConfigFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);

            if (!rootElement.isJsonObject()) {
                LOG.error("REI config root is not a JSON object — cannot patch (file: {})", reiConfigFile);

                return;
            }

            rootObject = rootElement.getAsJsonObject();

        } catch (JsonSyntaxException exception) {
            // JSON5 comments trip up Gson — worth calling out explicitly in the log
            LOG.error("Failed to parse REI config — likely contains JSON5 comments Gson cannot handle (file: {})", reiConfigFile, exception);

            return;
        } catch (IOException exception) {
            LOG.error("Failed to read REI config (file: {})", reiConfigFile, exception);

            return;
        }

        if (!rootObject.has(REI_CONFIG_SECTION) || !rootObject.get(REI_CONFIG_SECTION).isJsonObject()) {
            LOG.error("REI config missing expected '{}' section — structure may have changed in this REI version", REI_CONFIG_SECTION);

            return;
        }

        JsonObject appearanceObject = rootObject.getAsJsonObject(REI_CONFIG_SECTION);

        if (appearanceObject.has(REI_CONFIG_FIELD)) {
            JsonElement currentValue = appearanceObject.get(REI_CONFIG_FIELD);

            LOG.info("REI {}.{} current value: {} — patching to {}", REI_CONFIG_SECTION, REI_CONFIG_FIELD, currentValue, REI_COLUMNS_TARGET_VALUE);
        } else {
            LOG.warn("REI config field '{}.{}' not present — will be added", REI_CONFIG_SECTION, REI_CONFIG_FIELD);
        }

        appearanceObject.addProperty(REI_CONFIG_FIELD, REI_COLUMNS_TARGET_VALUE);

        try (BufferedWriter writer = Files.newBufferedWriter(reiConfigFile, StandardCharsets.UTF_8)) {
            GSON_WRITER.toJson(rootObject, writer);

            LOG.info("REI config patched successfully — note: JSON5 comments were stripped (file: {})", reiConfigFile);
        } catch (IOException e) {
            LOG.error("Failed to write patched REI config (file: {})", reiConfigFile, e);
        }
    }

    public static boolean isSkyblockerLoaded() {
        return FabricLoader.getInstance().isModLoaded(SKYBLOCKER_MOD_ID);
    }

    public static void setSkyblockerBazaarOverlayValue(boolean enabled) {
        if (!isSkyblockerLoaded()) {
            LOG.info("Skyblocker not loaded — skipping overlay toggle (requested: {})", enabled);

            return;
        }

        boolean current = isSkyblockerBazaarOverlayEnabled();

        if (current == enabled) {
            LOG.info("Skyblocker Bazaar overlay already {} — no change needed", enabled ? "enabled" : "disabled");

            return;
        }

        if (enabled) {
            tryEnableSkyblockerBazaarOverlay();
        } else {
            tryDisableSkyblockerBazaarOverlay();
        }
    }

    public static boolean isSkyblockerBazaarOverlayEnabled() {
        if (!isSkyblockerLoaded()) {
            LOG.info("isSkyblockerBazaarOverlayEnabled called but Skyblocker not loaded — returning false");
            return false;
        }
        try {
            SkyblockerConfig config = SkyblockerConfigManager.get();
            return config.uiAndVisuals.searchOverlay.enableBazaar;
        } catch (NoClassDefFoundError | NoSuchFieldError | Exception e) {
            // Skyblocker API changed — this happens on version mismatch
            LOG.error("Failed to read Skyblocker Bazaar overlay setting — Skyblocker API may have changed", e);
            return false;
        }
    }

    private static void tryDisableSkyblockerBazaarOverlay() {
        try {
            SkyblockerConfigManager.update(config -> config.uiAndVisuals.searchOverlay.enableBazaar = false);
            LOG.info("Skyblocker Bazaar overlay disabled");
            PlayerLogger.debug("Disabled Skyblocker Bazaar search overlay.", NotificationType.GUI, LOG);
        } catch (NoClassDefFoundError | NoSuchFieldError | Exception e) {
            LOG.error("Failed to disable Skyblocker Bazaar overlay — Skyblocker API may have changed", e);
        }
    }

    private static void tryEnableSkyblockerBazaarOverlay() {
        try {
            SkyblockerConfigManager.update(config -> config.uiAndVisuals.searchOverlay.enableBazaar = true);
            LOG.info("Skyblocker Bazaar overlay enabled");
            PlayerLogger.debug("Enabled Skyblocker Bazaar search overlay.", NotificationType.GUI, LOG);
        } catch (NoClassDefFoundError | NoSuchFieldError | Exception e) {
            LOG.error("Failed to enable Skyblocker Bazaar overlay — Skyblocker API may have changed", e);
        }
    }
}