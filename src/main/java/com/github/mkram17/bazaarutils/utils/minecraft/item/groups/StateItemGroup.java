package com.github.mkram17.bazaarutils.utils.minecraft.item.groups;

import com.github.mkram17.bazaarutils.utils.minecraft.item.StateItem;
import net.minecraft.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record StateItemGroup<S>(Set<Item> recognized, Map<S, StateItem> states) {
    /**
     * Unit type for StateGroup recognition — matches any configured item regardless of family.
     * <p>
     *     Identity-checked via {@code ==} in {@link #contains(Item)}, never iterated or queried for membership.
     * </p>
     */
    public static final Set<Item> ANY = Set.of();

    public boolean contains(Item item) {
        return recognized == ANY || recognized.contains(item);
    }

    public Item forState(S state, Item configured) {
        StateItem stateItem = states.getOrDefault(state, StateItem.configured());

        return switch (stateItem) {
            case StateItem.Fixed(Item item) -> item;
            case StateItem.Configured() -> configured;
        };
    }

    /**
     * A factory which defaults the recognized set to the unit type {@link #ANY},
     * albeit that it will encompass any item regardless of family or type,
     * and route it through the state map unconditionally.
     */
    public static <S> Builder<S> any() {
        return new Builder<>(ANY);
    }

    public static <S> Builder<S> of(Set<Item> recognized) {
        return new Builder<>(recognized);
    }

    public static <S> StateItemGroup<S> of(Set<Item> recognized, Map<S, StateItem> states) {
        return new StateItemGroup<>(recognized, states);
    }

    public static <S> StateItemGroup<S> of(Map<S, StateItem> states) {
        Set<Item> recognized = states.values().stream()
                .filter(state -> state instanceof StateItem.Fixed)
                .map(state -> ((StateItem.Fixed) state).item())
                .collect(Collectors.toSet());

        return new StateItemGroup<>(recognized, states);
    }

    public static final class Builder<S> {
        private final Set<Item> recognized;
        private final Map<S, StateItem> states = new LinkedHashMap<>();

        private Builder(Set<Item> recognized) {
            this.recognized = recognized;
        }

        public Builder<S> on(S state, Item item) {
            states.put(state, StateItem.of(item));
            return this;
        }

        public Builder<S> configured(S state) {
            states.put(state, StateItem.configured());
            return this;
        }

        public StateItemGroup<S> build() {
            return new StateItemGroup<>(recognized, Map.copyOf(states));
        }
    }
}