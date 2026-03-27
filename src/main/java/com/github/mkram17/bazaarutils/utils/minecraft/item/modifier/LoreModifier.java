// Adapted from https://github.com/meowdding/SkyOcean/blob/main/src/main/kotlin/me/owdding/skyocean/features/item/modifier/ItemModifier.kt
package com.github.mkram17.bazaarutils.utils.minecraft.item.modifier;

import com.github.mkram17.bazaarutils.utils.ListMerger;
import com.github.mkram17.bazaarutils.utils.minecraft.components.TextSearch;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

public interface LoreModifier extends AbstractItemModifier {    /**
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