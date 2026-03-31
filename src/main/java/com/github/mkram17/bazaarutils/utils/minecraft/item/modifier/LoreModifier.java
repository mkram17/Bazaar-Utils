// Adapted from https://github.com/meowdding/SkyOcean/blob/main/src/main/kotlin/me/owdding/skyocean/features/item/modifier/ItemModifier.kt
package com.github.mkram17.bazaarutils.utils.minecraft.item.modifier;

import com.github.mkram17.bazaarutils.utils.ListMerger;
import com.github.mkram17.bazaarutils.utils.minecraft.components.TextSearch;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockRarity;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface LoreModifier extends AbstractItemModifier {
    /**
     * Entry point for all lore mutations. Wraps {@code lore} in a {@link ListMerger},
     * runs {@code init}, flushes remaining lines, and writes the result back.
     */
    default Result withMerger(List<Component> lore, Function<ListMerger<Component>, Result> init) {
        var merger = new ListMerger<>(lore);

        var result = init.apply(merger);
        merger.addRemaining();
        lore.clear();
        lore.addAll(merger.destination());

        return result != null ? result : Result.UNMODIFIED;
    }

    /**
     * Entry point for all tooltip component mutations. Wraps {@code components} in a {@link ListMerger},
     * runs {@code init}, flushes remaining components, and writes the result back.
     */
    default Result withComponentMerger(List<ClientTooltipComponent> components, Function<ListMerger<ClientTooltipComponent>, Result> init) {
        var merger = new ListMerger<>(components);

        var result = init.apply(merger);
        merger.addRemaining();
        components.clear();
        components.addAll(merger.destination());

        return result != null ? result : Result.UNMODIFIED;
    }

    /**
     * Copies all remaining source lines to the destination.
     */
    default void copyAll(ListMerger<Component> merger) {
        while (merger.canRead()) merger.copy();
    }

    /**
     * Adds an empty separator line.
     */
    default void space(ListMerger<Component> merger) {
        merger.add(Component.empty());
    }

    /**
     * Copies lines until the predicate matches, then skips past the matching line without copying it.
     */
    default void addUntilAfter(ListMerger<Component> merger, Predicate<Component> predicate) {
        merger.addUntil(predicate);

        if (merger.canRead()) merger.read();
    }

    /**
     * Skips past all non-blank lines, then skips the blank separator line that follows.
     * Used to jump over a block of lore content to the space after it.
     */
    default void skipUntilAfterSpace(ListMerger<Component> merger) {
        while (merger.cursor() + 1 < merger.source().size() && !merger.peek().getString().isBlank()) {
            merger.read();
        }

        if (merger.cursor() + 1 < merger.source().size()) merger.read();
    }

    /**
     * Copies through the last line containing the rarity's display name.
     * Returns {@code true} if the rarity line was found.
     */
    default boolean addUntilRarityLine(ListMerger<Component> merger, SkyBlockRarity rarity) {
        List<Component> source = merger.source();

        String name = rarity.getDisplayName().toUpperCase();

        int lastIndex = -1;
        for (int i = source.size() - 1; i >= 0; i--) {
            if (source.get(i).getString().contains(name)) {
                lastIndex = i;
                break;
            }
        }

        if (lastIndex == -1) return false;

        merger.copyTo(lastIndex);

        return true;
    }

    /**
     * Copy lines up to and including the first line containing {@code marker}.
     * Returns {@code true} if the marker was found.
     */
    default boolean copyThrough(ListMerger<Component> merger, String marker) {
        int index = TextSearch.indexOf(merger.source(), marker);

        if (index == -1) return false;

        merger.copyTo(index);

        return true;
    }

    /**
     * Copy lines up to and including the last line containing {@code marker}.
     * Returns {@code true} if the marker was found.
     */
    default boolean copyThroughLast(ListMerger<Component> merger, String marker) {
        int index = TextSearch.lastIndexOf(merger.source(), marker);

        if (index == -1) return false;

        merger.copyTo(index);

        return true;
    }

    /**
     * Copy through the marker, then immediately add {@code lines} after it.
     * Returns {@code true} if the marker was found and lines were inserted.
     */
    default boolean insertAfter(ListMerger<Component> merger, String marker, List<Component> lines) {
        if (!copyThrough(merger, marker)) return false;

        lines.forEach(merger::add);

        return true;
    }

    /**
     * Convenience overload for a single inserted line.
     */
    default boolean insertAfter(ListMerger<Component> merger, String marker, Component line) {
        return insertAfter(merger, marker, List.of(line));
    }
}