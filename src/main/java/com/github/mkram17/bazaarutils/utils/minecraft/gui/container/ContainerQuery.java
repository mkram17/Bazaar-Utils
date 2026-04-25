package com.github.mkram17.bazaarutils.utils.minecraft.gui.container;

import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.components.TextSearch;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.advancements.criterion.MinMaxBounds.Ints;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class ContainerQuery {

    private final Ints slotRange;
    private final Predicate<ItemStack> filter;

    private ContainerQuery(Ints slotRange, Predicate<ItemStack> filter) {
        this.slotRange = slotRange;
        this.filter = filter;
    }

    public static ContainerQuery at(int slot) {
        return new ContainerQuery(Ints.exactly(slot), item -> true);
    }

    public static ContainerQuery range(int minInclusive, int maxInclusive) {
        return new ContainerQuery(Ints.between(minInclusive, maxInclusive), item -> true);
    }

    public ContainerQuery itemType(Item... wanted) {
        return chain(filter.and(stack -> {
            Item item = stack.getItem();

            for (Item type : wanted) {
                if (item == type) return true;
            }

            return false;
        }));
    }

    public ContainerQuery withCustomName(String... allowed) {
        return chain(filter.and(stack -> {
            Component data = stack.get(DataComponents.CUSTOM_NAME);

            if (data != null) {
                for (String name : allowed) {
                    if (data.getString().contains(name)) return true;
                }
            }

            return false;
        }));
    }

    public ContainerQuery withLore(String lore) {
        return chain(filter.and(stack -> {
            ItemLore data = stack.get(DataComponents.LORE);

            return data != null && !TextSearch.findSpanning(data.lines(), lore).isEmpty();
        }));
    }

    public Optional<ItemInfo> first(Container inventory) {
        int invSize = inventory.getContainerSize();
        int min = Math.max(0, slotRange.min().orElse(0));
        int max = Math.min(invSize - 1, slotRange.max().orElse(invSize - 1));

        for (int i = min; i <= max; i++) {
            ItemInfo item = SlotLookup.getInventoryItem(inventory, i);

            if (!item.itemStack().isEmpty() && filter.test(item.itemStack())) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public Optional<ItemInfo> first() {
        Optional<Container> inventory = ScreenManager.getScreenContainer();
        if (inventory.isEmpty()) return Optional.empty();

        return first(inventory.get());
    }

    public List<ItemInfo> all(Container inventory) {
        int invSize = inventory.getContainerSize();
        int min = Math.max(0, slotRange.min().orElse(0));
        int max = Math.min(invSize - 1, slotRange.max().orElse(invSize - 1));
        List<ItemInfo> out = new ArrayList<>();

        for (int i = min; i <= max; i++) {
            ItemInfo item = SlotLookup.getInventoryItem(inventory, i);
            if (!item.itemStack().isEmpty() && filter.test(item.itemStack())) {
                out.add(item);
            }
        }

        return out;
    }

    public List<ItemInfo> all() {
        Optional<Container> inventory = ScreenManager.getScreenContainer();

        if (inventory.isEmpty()) return new ArrayList<>();

        return all(inventory.get());
    }

    private ContainerQuery chain(Predicate<ItemStack> newFilter) {
        return new ContainerQuery(slotRange, newFilter);
    }
}