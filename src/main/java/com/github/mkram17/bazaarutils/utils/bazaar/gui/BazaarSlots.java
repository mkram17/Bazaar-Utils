package com.github.mkram17.bazaarutils.utils.bazaar.gui;

import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import lombok.AllArgsConstructor;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

import java.util.function.Function;

public class BazaarSlots {
    public record BazaarSlot(
            SlotLookup.IndexReference ref,
            Function<ContainerQuery, ContainerQuery> builder
    ) {
        public ContainerQuery query(Container container) {
            return builder.apply(ref.query(container));
        }
    }

    @AllArgsConstructor
    public enum OVERVIEW_PAGE {
        SELL_INVENTORY(new BazaarSlot(
                SlotLookup.IndexReference.fixed(47),
                (query) -> query
                        .itemType(Items.CHEST)
                        .withCustomName("Sell Inventory Now")
                )
        ),

        SELL_SACKS(new BazaarSlot(
                SlotLookup.IndexReference.fixed(48),
                (query) -> query
                        .itemType(Items.CAULDRON)
                        .withCustomName("Sell Sacks Now")
                )
        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum ITEM_PAGE {
        BUY_INSTANTLY(new BazaarSlot(
                SlotLookup.IndexReference.fixed(10),
                (query) -> query
                        .itemType(Items.GOLDEN_HORSE_ARMOR)
                        .withCustomName("Buy Instantly")
                )
        ),

        SELL_INSTANTLY(new BazaarSlot(
                SlotLookup.IndexReference.fixed(11),
                (query) -> query
                        .itemType(Items.HOPPER)
                        .withCustomName("Sell Instantly")
                )
        ),

        ITEM_DISPLAY(new BazaarSlot(
                SlotLookup.IndexReference.fixed(13),
                (query) -> query)
        ),

        CREATE_BUY_ORDER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(15),
                (query) -> query
                        .itemType(Items.FILLED_MAP)
                        .withCustomName("Create Buy Order")
                )
        ),

        CREATE_SELL_OFFER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(16),
                (query) -> query
                        .itemType(Items.MAP)
                        .withCustomName("Create Sell Offer")
                )
        ),

        SELL_SACKS(new BazaarSlot(
                SlotLookup.IndexReference.fixed(29),
                (query) -> query
                        .itemType(Items.CAULDRON)
                        .withCustomName("Sell Sacks Now")
                )
        ),

        VIEW_GRAPHS(new BazaarSlot(
                SlotLookup.IndexReference.fixed(33),
                (query) -> query
                        .itemType(Items.PAPER)
                        .withCustomName("View Graphs")
                )
        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum ITEMS_GROUP_PAGE {
        SELL_INVENTORY(
                new BazaarSlot(
                        SlotLookup.IndexReference.negativeOffset(6),
                        (query) -> query
                                .itemType(Items.CHEST)
                                .withCustomName("Sell Inventory Now")
                )
        ),

        SELL_SACKS(new BazaarSlot(
                SlotLookup.IndexReference.negativeOffset(2),
                (query) -> query
                        .itemType(Items.CAULDRON)
                        .withCustomName("Sell Sacks Now")
                )
        ),

        SWITCH_VIEW_MODE(new BazaarSlot(
                SlotLookup.IndexReference.negativeOffset(1),
                (query) -> query
                        .itemType(Items.IRON_ORE, Items.GOLD_ORE)
                        .withCustomName("Advanced Mode", "Direct Mode")
                        .withLore("Click to toggle view!")
                )
        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum ORDER_OPTIONS {
        FLIP_FILLED_BUY_ORDER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(15),
                (query) -> query
                        .itemType(Items.NAME_TAG)
                        .withCustomName("Flip Order")
                        .withLore("Click to pick new price!")
                )
        ),

        CANCEL_FILLED_BUY_ORDER (new BazaarSlot(
                SlotLookup.IndexReference.fixed(11),
                (query) -> query
                        .itemType(Items.RED_TERRACOTTA)
                        .withCustomName("Cancel Order")
                        .withLore("Cannot cancel order while there are goods to claim!")
                )
        ),

        FLIP_UNFILLED_BUY_ORDER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(15),
                (query) -> query
                        .itemType(Items.NAME_TAG)
                        .withCustomName("Flip Order")
                        .withLore("This order can't be flipped until it is filled!")
                )
        ),

        CANCEL_UNFILLED_BUY_ORDER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(11),
                (query) -> query
                        .itemType(Items.GREEN_TERRACOTTA)
                        .withCustomName("Cancel Order")
                        .withLore("x missing items.")
        )
        ),

        CANCEL_SELL_OFFER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(13),
                (query) -> query
                        .itemType(Items.GREEN_TERRACOTTA)
                        .withCustomName("Cancel Order")
                        .withLore("x items.")
                )
        );


        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum BUY_ORDER {
        INPUT_CUSTOM_AMOUNT(new BazaarSlot(
                SlotLookup.IndexReference.fixed(16),
                (query) -> query
                        .itemType(Items.OAK_SIGN)
                        .withCustomName("Custom Amount")
                )
        ),

        INPUT_CUSTOM_PRICE(new BazaarSlot(
                SlotLookup.IndexReference.fixed(16),
                (query) -> query
                        .itemType(Items.OAK_SIGN)
                        .withCustomName("Custom Price")
                )
        ),

        CONFIRM_BUY_ORDER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(13),
                (query) -> query
                        .withCustomName("Buy Order")
                )
        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum INSTANT_BUY {
        BUY_INVENTORY(new BazaarSlot(
                SlotLookup.IndexReference.fixed(14),
                (query) -> query
                        .itemType(Items.CHEST)
                        .withCustomName("Fill my inventory!")
                )
        ),

        INPUT_FILLING_AMOUNT(new BazaarSlot(
                SlotLookup.IndexReference.fixed(14),
                (query) -> query
                        .itemType(Items.OAK_SIGN)
                        .withCustomName("Fill my inventory!")
                )
        ),

        INPUT_CUSTOM_AMOUNT(new BazaarSlot(
                SlotLookup.IndexReference.fixed(16),
                (query) -> query
                        .itemType(Items.OAK_SIGN)
                        .withCustomName("Custom Amount")
                )
        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum SELL_OFFER {
        INPUT_CUSTOM_AMOUNT(new BazaarSlot(
                SlotLookup.IndexReference.fixed(16),
                (query) -> query
                        .itemType(Items.OAK_SIGN)
                        .withCustomName("Custom Amount")
                )
        ),

        INPUT_CUSTOM_PRICE(new BazaarSlot(
                SlotLookup.IndexReference.fixed(16),
                (query) -> query
                        .itemType(Items.OAK_SIGN)
                        .withCustomName("Custom Price")
                )
        ),

        CONFIRM_SELL_OFFER(new BazaarSlot(
                SlotLookup.IndexReference.fixed(13),
                (query) -> query
                        .withCustomName("Sell Offer")
                )
        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum INSTANT_SELL_ITEM {
        SELL_INVENTORY(new BazaarSlot(
                SlotLookup.IndexReference.fixed(15),
                (query) -> query
                        .itemType(Items.CHEST)
                        .withCustomName("Sell whole inventory!")
                )
        ),

        CONFIRM_SELL(new BazaarSlot(
                SlotLookup.IndexReference.fixed(13),
                query -> query
                        .itemType(Items.BARRIER)
                        .withCustomName("WARNING")
                )

        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }

    @AllArgsConstructor
    public enum INSTANT_SELL_GROUP {
        CONFIRM_SELL(new BazaarSlot(
                SlotLookup.IndexReference.fixed(11),
                (query) -> query
                        .itemType(Items.HOPPER)
                        .withCustomName("Selling whole inventory")
                )
        );

        public final BazaarSlot slot;

        public ContainerQuery query(Container container) {
            return slot.query(container);
        }
    }
}


