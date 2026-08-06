package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.util.api.SlotElement;
import com.github.mkram17.bazaarutils.config.util.api.SlotProviders;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;

import static com.github.mkram17.bazaarutils.config.util.api.SlotProviders.locked;

/**
 * Reproduces Hypixel's own layouts in the slot picker, so a player choosing where to put a button
 * sees the menu they will see in game. Each layout names only the slots Hypixel fills;
 * {@link SlotProviders#layout} supplies the filler and the bounds check.
 */
public final class SlotRendererProvider {
    private SlotRendererProvider() {}

    public static void register() {
        ResourcefulConfigUI.registerElementRenderer(
                Identifier.fromNamespaceAndPath("bazaarutils", "slot"),
                element -> {
                    SlotElement se = SlotElement.wrap(element);
                    return se != null ? new SlotRenderer(se) : null;
                }
        );

        registerElementProviders();
    }

    private static void registerElementProviders() {
        SlotProviders.registerDynamic(
                "bazaar:buy_order_amount",
                () -> ButtonsConfig.HelpersConfig.HELPERS_BUY_ORDER_AMOUNT_BUTTONS,
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 10 -> locked(Items.PAPER, 64, "Buy a stack!");
                    case 12 -> locked(Items.CHEST, 2, "Buy a big stack!");
                    case 14 -> locked(Items.CHEST, 16, "Buy a thousand!");
                    case 16 -> locked(Items.OAK_SIGN, "Custom Amount");
                    case 31 -> locked(Items.ARROW, "Go Back");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.registerDynamic(
                "bazaar:buy_order_price",
                () -> ButtonsConfig.HelpersConfig.HELPERS_BUY_ORDER_PRICE_BUTTONS,
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 10 -> locked(Items.PAPER, "Same as Top Order");
                    case 12 -> locked(Items.GOLD_NUGGET, "Top Order +0.1");
                    case 14 -> locked(Items.GOLDEN_HORSE_ARMOR, "5% of Spread");
                    case 16 -> locked(Items.OAK_SIGN, "Custom Price");
                    case 30 -> locked(Items.ARROW, "Go Back");
                    case 31 -> locked(Items.BARRIER, "Cancel Buy Order");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.register(
                "bazaar:buy_order_confirmation",
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 12 -> locked(Items.PAPER, "Buy Order");
                    case 30 -> locked(Items.ARROW, "Go Back");
                    case 31 -> locked(Items.BARRIER, "Cancel Buy Order");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.registerDynamic(
                "bazaar:instant_buy_amount",
                () -> ButtonsConfig.HelpersConfig.HELPERS_INSTANT_BUY_AMOUNT_BUTTONS,
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 10 -> locked(Items.PAPER, "Buy only one!");
                    case 12 -> locked(Items.PAPER, 64, "Buy a stack!");
                    case 14 -> locked(Items.CHEST, "Fill my inventory!");
                    case 16 -> locked(Items.OAK_SIGN, "Custom Amount");
                    case 31 -> locked(Items.ARROW, "Go Back");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.registerDynamic(
                "bazaar:sell_offer_amount",
                () -> ButtonsConfig.HelpersConfig.HELPERS_SELL_OFFER_AMOUNT_BUTTONS,
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 10 -> locked(Items.PAPER, 64, "Sell a stack!");
                    case 12 -> locked(Items.CHEST, "Sell half your inventory!");
                    case 14 -> locked(Items.CHEST, "Sell whole inventory!");
                    case 16 -> locked(Items.OAK_SIGN, "Custom Amount");
                    case 31 -> locked(Items.ARROW, "Go Back");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.registerDynamic(
                "bazaar:sell_offer_price",
                () -> ButtonsConfig.HelpersConfig.HELPERS_SELL_OFFER_PRICE_BUTTONS,
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 10 -> locked(Items.PAPER, "Same as Best Offer");
                    case 12 -> locked(Items.GOLD_NUGGET, "Best Offer -0.1");
                    case 14 -> locked(Items.GOLDEN_HORSE_ARMOR, "10% of Spread");
                    case 16 -> locked(Items.OAK_SIGN, "Custom Price");
                    case 30 -> locked(Items.ARROW, "Go Back");
                    case 31 -> locked(Items.BARRIER, "Cancel Sell Offer");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.register(
                "bazaar:sell_offer_confirmation",
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 12 -> locked(Items.PAPER, "Sell Offer");
                    case 30 -> locked(Items.ARROW, "Go Back");
                    case 31 -> locked(Items.BARRIER, "Cancel Sell Offer");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.register(
                "bazaar:instant_sell_amount",
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 11 -> locked(Items.PAPER, "Sell a stack!");
                    case 13 -> locked(Items.CHEST, "Sell half your inventory!");
                    case 15 -> locked(Items.CHEST, "Sell whole inventory!");
                    case 31 -> locked(Items.ARROW, "Go Back");
                    default -> ItemStack.EMPTY;
                })
        );

        SlotProviders.registerDynamic(
                "bazaar:flip_filled_buy_order",
                () -> ButtonsConfig.HelpersConfig.HELPERS_FLIP_ORDER_PRICE_BUTTONS,
                SlotProviders.layout(slotIndex -> switch (slotIndex) {
                    case 11 -> locked(Items.RED_TERRACOTTA, "Cancel Order");
                    case 15 -> locked(Items.NAME_TAG, "Flip Order");
                    case 31 -> locked(Items.ARROW, "Go Back");
                    default -> ItemStack.EMPTY;
                })
        );
    }
}
