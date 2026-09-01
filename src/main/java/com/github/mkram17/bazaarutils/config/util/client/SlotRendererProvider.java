package com.github.mkram17.bazaarutils.config.util.client;

import com.github.mkram17.bazaarutils.config.features.gui.ButtonsConfig;
import com.github.mkram17.bazaarutils.config.util.api.SlotElement;
import com.github.mkram17.bazaarutils.config.util.api.SlotProviders;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.teamresourceful.resourcefulconfig.api.client.ResourcefulConfigUI;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;

@PreInitModule
public final class SlotRendererProvider {
    public SlotRendererProvider() {
        register();
    }

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
                (slotIndex, selectedSlot) -> {
                    if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

                    return switch (slotIndex) {
                        case 10 -> SlotProviders.stack(Items.PAPER, 64).named("Buy a stack!").locked().build();
                        case 12 -> SlotProviders.stack(Items.CHEST, 2).named("Buy a big stack!").locked().build();
                        case 14 -> SlotProviders.stack(Items.CHEST, 16).named("Buy a thousand!").locked().build();
                        case 16 -> SlotProviders.stack(Items.OAK_SIGN).named("Custom Amount").locked().build();
                        case 31 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                        default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
                    };
                }
        );

        SlotProviders.registerDynamic(
                "bazaar:buy_order_price",
                () -> ButtonsConfig.HelpersConfig.HELPERS_BUY_ORDER_PRICE_BUTTONS,
                (slotIndex, selectedSlot) -> {
                    if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

                    return switch (slotIndex) {
                        case 10 -> SlotProviders.stack(Items.PAPER).named("Same as Top Order").locked().build();
                        case 12 -> SlotProviders.stack(Items.GOLD_NUGGET).named("Top Order +0.1").locked().build();
                        case 14 -> SlotProviders.stack(Items.GOLDEN_HORSE_ARMOR).named("5% of Spread").locked().build();
                        case 16 -> SlotProviders.stack(Items.OAK_SIGN).named("Custom Price").locked().build();
                        case 30 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                        case 31 -> SlotProviders.stack(Items.BARRIER).named("Cancel Buy Order").locked().build();
                        default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
                    };
                }
        );

        SlotProviders.register("bazaar:buy_order_confirmation", (slotIndex, selectedSlot) -> {
            if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

            return switch (slotIndex) {
                case 12 -> SlotProviders.stack(Items.PAPER).named("Buy Order").locked().build();
                case 30 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                case 31 -> SlotProviders.stack(Items.BARRIER).named("Cancel Buy Order").locked().build();
                default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
            };
        });

        SlotProviders.registerDynamic(
                "bazaar:instant_buy_amount",
                () -> ButtonsConfig.HelpersConfig.HELPERS_INSTANT_BUY_AMOUNT_BUTTONS,
                (slotIndex, selectedSlot) -> {
                    if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

                    return switch (slotIndex) {
                        case 10 -> SlotProviders.stack(Items.PAPER).named("Buy only one!").locked().build();
                        case 12 -> SlotProviders.stack(Items.PAPER, 64).named("Buy a stack!").locked().build();
                        case 14 -> SlotProviders.stack(Items.CHEST).named("Fill my inventory!").locked().build();
                        case 16 -> SlotProviders.stack(Items.OAK_SIGN).named("Custom Amount").locked().build();
                        case 31 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                        default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
                    };
                }
        );

        SlotProviders.registerDynamic(
                "bazaar:sell_offer_amount",
                () -> ButtonsConfig.HelpersConfig.HELPERS_SELL_OFFER_AMOUNT_BUTTONS,
                (slotIndex, selectedSlot) -> {
                    if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

                    return switch (slotIndex) {
                        case 10 -> SlotProviders.stack(Items.PAPER, 64).named("Sell a stack!").locked().build();
                        case 12 -> SlotProviders.stack(Items.CHEST).named("Sell half your inventory!").locked().build();
                        case 14 -> SlotProviders.stack(Items.CHEST).named("Sell whole inventory!").locked().build();
                        case 16 -> SlotProviders.stack(Items.OAK_SIGN).named("Custom Amount").locked().build();
                        case 31 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                        default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
                    };
                }
        );

        SlotProviders.registerDynamic(
                "bazaar:sell_offer_price",
                () -> ButtonsConfig.HelpersConfig.HELPERS_SELL_OFFER_PRICE_BUTTONS,
                (slotIndex, selectedSlot) -> {
                    if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

                    return switch (slotIndex) {
                        case 10 -> SlotProviders.stack(Items.PAPER).named("Same as Best Offer").locked().build();
                        case 12 -> SlotProviders.stack(Items.GOLD_NUGGET).named("Best Offer -0.1").locked().build();
                        case 14 -> SlotProviders.stack(Items.GOLDEN_HORSE_ARMOR).named("10% of Spread").locked().build();
                        case 16 -> SlotProviders.stack(Items.OAK_SIGN).named("Custom Price").locked().build();
                        case 30 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                        case 31 -> SlotProviders.stack(Items.BARRIER).named("Cancel Sell Offer").locked().build();
                        default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
                    };
                }
        );

        SlotProviders.register("bazaar:sell_offer_confirmation", (slotIndex, selectedSlot) -> {
            if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

            return switch (slotIndex) {
                case 12 -> SlotProviders.stack(Items.PAPER).named("Sell Offer").locked().build();
                case 30 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                case 31 -> SlotProviders.stack(Items.BARRIER).named("Cancel Sell Offer").locked().build();
                default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
            };
        });

        SlotProviders.register("bazaar:instant_sell_amount", (slotIndex, selectedSlot) -> {
            if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

            return switch (slotIndex) {
                case 11 -> SlotProviders.stack(Items.PAPER).named("Sell a stack!").locked().build();
                case 13 -> SlotProviders.stack(Items.CHEST).named("Sell half your inventory!").locked().build();
                case 15 -> SlotProviders.stack(Items.CHEST).named("Sell whole inventory!").locked().build();
                case 31 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
            };
        });

        SlotProviders.registerDynamic(
                "bazaar:flip_filled_buy_order",
                () -> ButtonsConfig.HelpersConfig.HELPERS_FLIP_ORDER_PRICE_BUTTONS,
                (slotIndex, selectedSlot) -> {
                    if (slotIndex < 0 || slotIndex > 35) return ItemStack.EMPTY;

                    return switch (slotIndex) {
                        case 11 -> SlotProviders.stack(Items.RED_TERRACOTTA).named("Cancel Order").locked().build();
                        case 15 -> SlotProviders.stack(Items.NAME_TAG).named("Flip Order").locked().build();
                        case 31 -> SlotProviders.stack(Items.ARROW).named("Go Back").locked().build();
                        default -> SlotProviders.stack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().build();
                    };
                }
        );
    }
}