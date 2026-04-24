package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.events.ScreenChangeEvent;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Items;

import java.util.function.Predicate;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

// Utility class for current screen info
public class ScreenInfo {
    @Getter
    private static final ScreenInfo currentScreenInfo = new ScreenInfo();
    private static boolean initialized = false;

    @Getter
    private Screen screen;
    private Screen previousScreen;

    public enum BazaarMenuType {
        BUY_ORDER("How many do you want?"),
        INSTA_BUY("➜ Insta"),
        ORDER_SCREEN("Bazaar Orders"),
        SELL_SETUP("At what price are you selling?"),
        CONFIRM_SELL_OFFER("Confirm Sell Offer"),
        CONFIRM_BUY_OFFER("Confirm Buy Order"),
        ORDER_PRICE("How much do you want to pay?"),
        FLIP_GUI("Order options"),
        BAZAAR_MAIN_PAGE("Bazaar ➜ "),
        BAZAAR_SETTINGS_PAGE("Settings"),
        ITEM_GROUP_PAGE(info -> {
            String name = info.getContainerName();
            if (name == null || !name.contains("➜")) return false;
            return !GUIUtils.getChestItem(34).getItem().equals(Items.FURNACE);
        }),

        INDIVIDUAL_ITEM(info -> {
            String name = info.getContainerName();
            if (name == null || !name.contains("➜")) return false;
            var item = GUIUtils.getChestItem(33);
            if (item == null || item.getCustomName() == null) return false;

            return item.getCustomName().getString().equals("View Graphs");
        });

        @Getter
        private final String titleString;
        private final Predicate<ScreenInfo> matcher;

        BazaarMenuType(String titleString) {
            this.titleString = titleString;
            this.matcher = info -> {
                String n = info.getContainerName();
                return n != null && n.contains(titleString);
            };
        }

        BazaarMenuType(Predicate<ScreenInfo> matcher) {
            this.titleString = null;
            this.matcher = matcher;
        }

        public boolean matches(ScreenInfo info) {
            return matcher.test(info);
        }
    }

    private ScreenInfo() {
        // singleton
    }

    public static void initialize(Screen initialScreen) {
        if (initialized) return;
        currentScreenInfo.screen = initialScreen;
        EVENT_BUS.subscribe(currentScreenInfo);
        initialized = true;
    }

    @EventHandler
    private void onScreenChange(ScreenChangeEvent event) {
        this.previousScreen = this.screen;
        this.screen = event.getNewScreen();
    }

    /**
     * Gets a temporary ScreenInfo object representing the previous screen.
     * Returns null if there was no previous screen.
     */
    public ScreenInfo getPreviousScreenInfo() {
        if (previousScreen == null) {
            return null;
        }
        ScreenInfo prevInfo = new ScreenInfo();
        prevInfo.screen = this.previousScreen;
        return prevInfo;
    }

    /*
     * returns true if the user is in any of the specified types of bazaar menus
     */
    public boolean inMenu(BazaarMenuType... types) {
        for (BazaarMenuType bazaarMenuType : types) {
            if (bazaarMenuType.matches(this)) {
                return true;
            }
        }
        return false;
    }

    public boolean inBazaar() {
        return inMenu(
                BazaarMenuType.BUY_ORDER,
                BazaarMenuType.FLIP_GUI,
                BazaarMenuType.INSTA_BUY,
                BazaarMenuType.BAZAAR_MAIN_PAGE,
                BazaarMenuType.ORDER_SCREEN,
                BazaarMenuType.SELL_SETUP,
                BazaarMenuType.CONFIRM_SELL_OFFER,
                BazaarMenuType.CONFIRM_BUY_OFFER,
                BazaarMenuType.ORDER_PRICE,
                BazaarMenuType.ITEM_GROUP_PAGE,
                BazaarMenuType.INDIVIDUAL_ITEM
        );
    }

    public String getContainerName() {
        if (screen == null || screen.getTitle() == null)
            return null;
        return Util.removeFormatting(screen.getTitle().getString());
    }
}
