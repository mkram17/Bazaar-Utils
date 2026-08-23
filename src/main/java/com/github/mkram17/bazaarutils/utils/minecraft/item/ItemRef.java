package com.github.mkram17.bazaarutils.utils.minecraft.item;

import com.github.mkram17.bazaarutils.utils.minecraft.item.groups.StateItemGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public sealed interface ItemRef {
    record Direct(ItemStackTemplate template) implements ItemRef {}
    record ById(Supplier<String> id) implements ItemRef {}
    record Stateful<S>(Optional<ItemRef> source, Supplier<S> state, List<StateItemGroup<S>> groups) implements ItemRef {
        static <S> Stateful<S> of(Supplier<S> state, List<StateItemGroup<S>> groups) {
            return new Stateful<>(Optional.empty(), state, groups);
        }
    }

    static ItemRef of(ItemStackTemplate template) {
        return new Direct(template);
    }

    static ItemRef of(ItemStack stack) {
        return new Direct(ItemStackTemplate.fromNonEmptyStack(stack));
    }

    static ItemRef of(String id) {
        return new ById(() -> id);
    }

    static ItemRef of(Supplier<String> id) {
        return new ById(id);
    }

    @SafeVarargs
    static <S> ItemRef of(ItemRef source, Supplier<S> state, StateItemGroup<S>... groups) {
        return new Stateful<>(Optional.of(source), state, List.of(groups));
    }

    static <S> ItemRef of(Supplier<S> state, StateItemGroup<S> group) {
        return Stateful.of(state, List.of(group));
    }

    @SafeVarargs
    static <S> ItemRef of(Supplier<S> state, StateItemGroup<S>... groups) {
        return Stateful.of(state, List.of(groups));
    }
}