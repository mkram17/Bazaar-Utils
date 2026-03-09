package com.github.mkram17.bazaarutils.utils.minecraft.gui.container;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotLookup;
import com.github.mkram17.bazaarutils.utils.minecraft.components.TextSearch;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.NumberRange.IntRange;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class ContainerQuery {

    private final IntRange          slotRange;
    private final Predicate<ItemStack> filter;
    private final List<String>      description;

    private ContainerQuery(IntRange slotRange, Predicate<ItemStack> filter, List<String> description) {
        this.slotRange   = slotRange;
        this.filter      = filter;
        this.description = List.copyOf(description);
    }

    public static ContainerQuery at(int slot) {
        return new ContainerQuery(
                IntRange.exactly(slot),
                item -> true,
                List.of("slot=" + slot));
    }

    public static ContainerQuery range(int minInclusive, int maxInclusive) {
        return new ContainerQuery(
                IntRange.between(minInclusive, maxInclusive),
                item -> true,
                List.of("slots=" + minInclusive + ".." + maxInclusive));
    }

    public ContainerQuery itemType(Item... wanted) {
        String names = java.util.Arrays.stream(wanted)
                .map(item -> item.toString())
                .reduce((a, b) -> a + "|" + b)
                .orElse("none");

        return chain(filter.and(stack -> {
            Item item = stack.getItem();
            for (Item type : wanted) {
                if (item == type) return true;
            }
            return false;
        }), "itemType[" + names + "]");
    }

    public ContainerQuery withCustomName(String... allowed) {
        String names = String.join("|", allowed);

        return chain(filter.and(stack -> {
            Text data = stack.get(DataComponentTypes.CUSTOM_NAME);
            if (data != null) {
                for (String name : allowed) {
                    if (data.getString().contains(name)) return true;
                }
            }
            return false;
        }), "name[" + names + "]");
    }

    public ContainerQuery withLore(String lore) {
        return chain(filter.and(stack -> {
            LoreComponent data = stack.get(DataComponentTypes.LORE);
            return data != null && !TextSearch.findSpanning(data.lines(), lore).isEmpty();
        }), "lore[" + lore + "]");
    }

    public Optional<ItemStack> first(Inventory inventory) {
        int invSize = inventory.size();
        int min = Math.max(0, slotRange.getMin().orElse(0));
        int max = Math.min(invSize - 1, slotRange.getMax().orElse(invSize - 1));

        for (int i = min; i <= max; i++) {
            ItemInfo item = SlotLookup.getInventoryItem(inventory, i);
            if (!item.itemStack().isEmpty() && filter.test(item.itemStack())) {
                return Optional.of(item.itemStack());
            }
        }

        return Optional.empty();
    }

    public Optional<ItemStack> first() {
        Optional<Inventory> inventory = ScreenManager.getScreenContainer();
        if (inventory.isEmpty()) return Optional.empty();
        return first(inventory.get());
    }

    public List<ItemStack> all(Inventory inventory) {
        int invSize = inventory.size();
        int min = Math.max(0, slotRange.getMin().orElse(0));
        int max = Math.min(invSize - 1, slotRange.getMax().orElse(invSize - 1));
        List<ItemStack> out = new ArrayList<>();

        for (int i = min; i <= max; i++) {
            ItemInfo item = SlotLookup.getInventoryItem(inventory, i);
            if (!item.itemStack().isEmpty() && filter.test(item.itemStack())) {
                out.add(item.itemStack());
            }
        }

        return out;
    }

    public List<ItemStack> all() {
        Optional<Inventory> inventory = ScreenManager.getScreenContainer();
        if (inventory.isEmpty()) return new ArrayList<>();
        return all(inventory.get());
    }

    public String describe() {
        if (description.isEmpty()) return "empty";

        if (description.size() == 1) return description.getFirst();

        String slotPart = description.getFirst();
        String filterPart = description.subList(1, description.size())
                .stream()
                .reduce((a, b) -> a + " && " + b)
                .orElse("");

        return slotPart + " → " + filterPart;
    }

    @Override
    public String toString() {
        return "ContainerQuery{" + describe() + "}";
    }

    private ContainerQuery chain(Predicate<ItemStack> newFilter, String desc) {
        List<String> newDesc = new ArrayList<>(description);
        newDesc.add(desc);
        return new ContainerQuery(slotRange, newFilter, newDesc);
    }
}