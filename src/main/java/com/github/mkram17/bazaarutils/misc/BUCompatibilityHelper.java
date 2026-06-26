package com.github.mkram17.bazaarutils.misc;

import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.*;
import de.hysky.skyblocker.config.SkyblockerConfig;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BUCompatibilityHelper {
    private static final String REI_MOD_ID = "roughlyenoughitems";
    public static final String SKYBLOCKER_MOD_ID = "skyblocker";
    private static final String REI_CONFIG_FILENAME = "roughlyenoughitems/config.json5";
    private static final String REI_CONFIG_SECTION = "appearance";
    private static final String REI_CONFIG_FIELD = "horizontalEntriesBoundariesColumns";
    private static final int HORIZONTALENTRIESBOUNDARIESCOLUMS_VALUE = 16;
    public static final String FIRMAMENT_MODID = "firmament";
    @Getter

    private static final Gson GSON_WRITER = new GsonBuilder().setPrettyPrinting().create();

    public static void initializePatches(){
        if (FabricLoader.getInstance().isModLoaded(REI_MOD_ID)) {
            Util.logMessage("REI detected. Attempting to modify REI config.");
            modifyReiConfigWithGson();
        }
    }

    //TODO use dependency instead of reflection
    private static void modifyReiConfigWithGson() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path reiConfigFile = configDir.resolve(REI_CONFIG_FILENAME);

        if (!Files.exists(reiConfigFile)) {
            Util.notifyError("Could not find REI config file at: " + reiConfigFile, new Throwable());
            return;
        }

        JsonObject rootObject = null;

        try (BufferedReader reader = Files.newBufferedReader(reiConfigFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);

            if (rootElement.isJsonObject()) {
                rootObject = rootElement.getAsJsonObject();
            } else {
                Util.notifyError("REI config root is not a JSON object: " + reiConfigFile, new Throwable());
                return;
            }

        } catch (JsonSyntaxException e) {
            Util.notifyError("Failed to parse REI config file (likely due to non-standard JSON5 features like comments that Gson couldn't handle, or actual syntax errors): " + reiConfigFile, e);
            return;
        } catch (IOException e) {
            Util.notifyError("Failed to read REI config file: " + reiConfigFile, e);
            return;
        }

        try {
            if (rootObject != null && rootObject.has(REI_CONFIG_SECTION) && rootObject.get(REI_CONFIG_SECTION).isJsonObject()) {
                JsonObject appearanceObject = rootObject.getAsJsonObject(REI_CONFIG_SECTION);

                if (appearanceObject.has(REI_CONFIG_FIELD)) {
                    JsonElement currentValue = appearanceObject.get(REI_CONFIG_FIELD);
                    Util.logMessage("Current REI value for '" + REI_CONFIG_SECTION + "." + REI_CONFIG_FIELD + "': " + currentValue);
                } else {
                    Util.notifyError("Key '" + REI_CONFIG_SECTION + "." + REI_CONFIG_FIELD + "' not found in REI config.", new Throwable());
                }

                appearanceObject.addProperty(REI_CONFIG_FIELD, HORIZONTALENTRIESBOUNDARIESCOLUMS_VALUE);
                Util.logMessage("Set REI value for '" + REI_CONFIG_SECTION + "." + REI_CONFIG_FIELD + "' to: " + HORIZONTALENTRIESBOUNDARIESCOLUMS_VALUE);

            } else {
                Util.notifyError("REI config structure unexpected. Missing '" + REI_CONFIG_SECTION + "' object.", new Throwable());
                return;
            }
        } catch (Exception e) {
            Util.notifyError("Error modifying the JSON structure in memory.", e);
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(reiConfigFile, StandardCharsets.UTF_8)) {
            GSON_WRITER.toJson(rootObject, writer);
            Util.logMessage("Successfully saved modified REI config (comments removed): " + reiConfigFile);
        } catch (IOException e) {
            Util.notifyError("Failed to write modified REI config file: " + reiConfigFile, e);
        }
    }

    public static boolean isSkyblockerLoaded() {
        return FabricLoader.getInstance().isModLoaded(SKYBLOCKER_MOD_ID);
    }
    //true == success, false == failure

    public static void setSkyblockerBazaarOverlayValue(boolean enabled) {
        if (!isSkyblockerLoaded()) {
            Util.logMessage("Skyblocker not loaded, cannot change its config.");
            return;
        }
        if(enabled) {
            if(isSkyblockerBazaarOverlayEnabled()) {
                Util.logMessage("Skyblocker Bazaar Overlay already enabled.");
                return;
            }
            tryEnableSkyblockerBazaarOverlay();
        } else {
            if(!isSkyblockerBazaarOverlayEnabled()) {
                Util.logMessage("Skyblocker Bazaar Overlay already disabled.");
                return;
            }
            tryDisableSkyblockerBazaarOverlay();
        }
    }

    public static boolean isSkyblockerBazaarOverlayEnabled() {
        if (!isSkyblockerLoaded()) {
            Util.logMessage("Skyblocker not loaded, cannot check its config.");
            return false;
        }
        try {
            SkyblockerConfig skyblockerConfig = SkyblockerConfigManager.get();
            return skyblockerConfig.uiAndVisuals.searchOverlay.enableBazaar;
        } catch (NoClassDefFoundError | NoSuchFieldError | Exception e) {
            Util.notifyError("Failed to access Skyblocker config setting.", e);
            return false;
        }
    }
    private static void tryDisableSkyblockerBazaarOverlay() {
        if (!isSkyblockerLoaded()) {
            Util.logMessage("Skyblocker not loaded, cannot change its config.");
            return;
        }
        try {
            boolean currentValue = isSkyblockerBazaarOverlayEnabled();
            PlayerActionUtil.notifyAll("Skyblocker Bazaar Overlay current state: " + currentValue, Util.notificationTypes.GUI);

            if (currentValue) {
                SkyblockerConfigManager.update((config) -> config.uiAndVisuals.searchOverlay.enableBazaar = false);
                PlayerActionUtil.notifyAll("Attempting to disable Skyblocker Bazaar Overlay...", Util.notificationTypes.GUI);

                PlayerActionUtil.notifyAll("Disabled Skyblocker Bazaar search overlay.", Util.notificationTypes.GUI);
            } else {
                PlayerActionUtil.notifyAll("Skyblocker Bazaar Overlay already disabled.", Util.notificationTypes.GUI);
            }
        } catch (NoClassDefFoundError | NoSuchFieldError | Exception e) {
            Util.notifyError("Failed to access or modify Skyblocker config setting.", e);
        }
    }

    private static void tryEnableSkyblockerBazaarOverlay() {
        if (!FabricLoader.getInstance().isModLoaded(SKYBLOCKER_MOD_ID)) {
            Util.logMessage("Skyblocker not loaded, cannot enable its config setting.");
            return;
        }
        try {
            if (!isSkyblockerBazaarOverlayEnabled()) {
                Util.logMessage("Attempting to enable Skyblocker Bazaar Overlay...");
                SkyblockerConfigManager.update((x) -> x.uiAndVisuals.searchOverlay.enableBazaar = true);

                PlayerActionUtil.notifyAll("Enabled Skyblocker Bazaar search overlay.", Util.notificationTypes.GUI);
            } else {
                Util.logMessage("Skyblocker Bazaar Overlay already enabled.");
            }

        } catch (NoClassDefFoundError | NoSuchFieldError | Exception e) {
            Util.notifyError("Failed to access or modify Skyblocker config setting (enable attempt).", e);
        }

    }
    
}
