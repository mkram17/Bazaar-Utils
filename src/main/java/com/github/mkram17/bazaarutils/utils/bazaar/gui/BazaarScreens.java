package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BazaarScreens {
    public static final Pattern AMOUNT_PATTERN = Pattern.compile("Amount: (?<amount>[0-9,.]+)x");
    private static final Pattern SELL_LIMIT_PATTERN = Pattern.compile("Inventory: (?<amount>[0-9,.]+) items");
    private static final Pattern PURCHASE_LIMIT_PATTERN = Pattern.compile("Buy up to (?<amount>[0-9,.]+)x.");

    private static boolean initialized = false;

    private BazaarScreens() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        for (ScreenType screen : ALL) {
            ScreenManager.register(screen);
        }
    }

//    Screens

    public static final ScreenType MAIN_PAGE = new ScreenType.Builder()
            .name("MAIN_PAGE")
            .genericContainer()
            .containerTitle("Bazaar ➜ ")
            .build();

    public static final ScreenType SETTINGS_PAGE = new ScreenType.Builder()
            .name("SETTINGS_PAGE")
            .genericContainer()
            .containerTitle("Settings")
            .build();

    public static final ScreenType ORDERS_PAGE = new ScreenType.Builder()
            .name("ORDERS_PAGE")
            .genericContainer()
            .containerTitle("Bazaar Orders")
            .build();

//    Browsing stuff

    public static final ScreenType ITEM_PAGE = new ScreenType.Builder()
            .name("ITEM_PAGE")
            .genericContainer()
            .containerTitle(" ➜ ")
            .containerQuery("VIEW_GRAPHS", BazaarSlots.ITEM_PAGE.VIEW_GRAPHS::query)
            .build();

    public static final ScreenType ITEMS_GROUP_PAGE = new ScreenType.Builder()
            .name("ITEMS_GROUP_PAGE")
            .genericContainer()
            .containerTitle(" ➜ ")
            .containerQuery("SWITCH_VIEW_MODE", BazaarSlots.ITEMS_GROUP_PAGE.SWITCH_VIEW_MODE::query)
            .build();

//    Buying stuff

    public static final ScreenType BUY_ORDER_AMOUNT = new ScreenType.Builder()
            .name("BUY_ORDER_AMOUNT")
            .genericContainer()
            .containerTitle("How many do you want?")
            .build();

    public static final ScreenType BUY_ORDER_PRICE = new ScreenType.Builder()
            .name("BUY_ORDER_PRICE")
            .genericContainer()
            .containerTitle("How much do you want to pay?")
            .build();

    public static final ScreenType BUY_ORDER_CONFIRMATION = new ScreenType.Builder()
            .name("BUY_ORDER_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm Buy Order")
            .build();

    public static final ScreenType PENDING_BUY_ORDER_OPTIONS = new ScreenType.Builder()
            .name("PENDING_BUY_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("FLIP_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_UNFILLED_BUY_ORDER::query)
            .containerQuery("CANCEL_UNFILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_UNFILLED_BUY_ORDER::query)
            .build();

    public static final ScreenType COMPLETED_BUY_ORDER_OPTIONS = new ScreenType.Builder()
            .name("COMPLETED_BUY_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("FLIP_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.FLIP_FILLED_BUY_ORDER::query)
            .containerQuery("CANCEL_FILLED_BUY_ORDER", BazaarSlots.ORDER_OPTIONS.CANCEL_FILLED_BUY_ORDER::query)
            .build();

    public static final ScreenType INSTANT_BUY = new ScreenType.Builder()
            .name("INSTANT_BUY")
            .genericContainer()
            .containerTitle("➜ Inst")
            .containerQuery("INPUT_CUSTOM_AMOUNT", BazaarSlots.INSTANT_BUY.INPUT_CUSTOM_AMOUNT::query)
            .build();

//    Selling stuff

    public static final ScreenType SELL_OFFER_AMOUNT = new ScreenType.Builder()
            .name("SELL_OFFER_AMOUNT")
            .genericContainer()
            .containerTitle("How many are you selling?")
            .build();

    public static final ScreenType SELL_OFFER_PRICE = new ScreenType.Builder()
            .name("SELL_OFFER_PRICE")
            .genericContainer()
            .containerTitle("At what price are you selling?")
            .build();

    public static final ScreenType SELL_OFFER_CONFIRMATION = new ScreenType.Builder()
            .name("SELL_OFFER_CONFIRMATION")
            .genericContainer()
            .containerTitle("Confirm Sell Offer")
            .build();

    public static final ScreenType SELL_OFFER_OPTIONS = new ScreenType.Builder()
            .name("SELL_ORDER_OPTIONS")
            .genericContainer()
            .containerTitle("Order options")
            .containerQuery("CANCEL_SELL_OFFER", BazaarSlots.ORDER_OPTIONS.CANCEL_SELL_OFFER::query)
            .build();

    public static final ScreenType INSTANT_SELL = new ScreenType.Builder()
            .name("INSTANT_SELL")
            .genericContainer()
            .containerTitle("➜ Inst")
            .containerQuery("SELL_INVENTORY", BazaarSlots.INSTANT_SELL.SELL_INVENTORY::query)
            .build();

    public static final Set<ScreenType> ALL = Set.of(
            MAIN_PAGE,
            SETTINGS_PAGE,
            ORDERS_PAGE,

            ITEM_PAGE,
            ITEMS_GROUP_PAGE,

            BUY_ORDER_AMOUNT,
            BUY_ORDER_PRICE,
            BUY_ORDER_CONFIRMATION,
            PENDING_BUY_ORDER_OPTIONS,
            COMPLETED_BUY_ORDER_OPTIONS,
            INSTANT_BUY,

            SELL_OFFER_AMOUNT,
            SELL_OFFER_PRICE,
            SELL_OFFER_CONFIRMATION,
            SELL_OFFER_OPTIONS,

            INSTANT_SELL
    );

    public static Optional<Double> findOptionAmount(ItemStack option) {
        ItemLore lore = option.getComponents().get(DataComponents.LORE);

        if (lore != null) {
            String joined = lore.lines().stream().map(Component::getString).collect(Collectors.joining((" ")));
            Matcher matcher = AMOUNT_PATTERN.matcher(joined);

            if (matcher.find()) {
                try {
                    return Optional.of(Double.parseDouble(matcher.group("amount").replace(",", "")));
                } catch(NumberFormatException exception) {
                    Util.notifyError("Failed to parse the amount specifier from " + option.getCustomName(), exception);
                }
            }
        }

        return Optional.empty();
    }

    public static Optional<Integer> findBuyOrderAmountLimit(ItemStack inputSign) {
        ItemLore lore = inputSign.getComponents().get(DataComponents.LORE);

        if (lore != null) {
            String joined = lore.lines().stream().map(Component::getString).collect(Collectors.joining((" ")));
            Matcher matcher = PURCHASE_LIMIT_PATTERN.matcher(joined);

            if (matcher.find()) {
                try {
                    return Optional.of(Integer.parseInt(matcher.group("amount").replace(",", "")));
                } catch (NumberFormatException exception) {
                    Util.notifyError("Failed to parse the amount limit specifier from " + inputSign.getCustomName(), exception);
                }
            }
        }

        return Optional.empty();
    }

    // Could be of use, but is not, as generally you cannot hold any further than what the Bazaar would allow you to sell order.
    public static Optional<Integer> findSellAmountLimit(ItemStack inputSign) {
        ItemLore lore = inputSign.getComponents().get(DataComponents.LORE);

        if (lore != null) {
            String joined = lore.lines().stream().map(Component::getString).collect(Collectors.joining((" ")));
            Matcher matcher = SELL_LIMIT_PATTERN.matcher(joined);

            if (matcher.find()) {
                try {
                    return Optional.of(Integer.parseInt(matcher.group("amount").replace(",", "")));
                } catch (NumberFormatException exception) {
                    Util.notifyError("Failed to parse the amount limit specifier from " + inputSign.getCustomName(), exception);
                }
            }
        }

        return Optional.empty();
    }
}
