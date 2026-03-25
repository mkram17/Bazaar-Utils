package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface ScreenType extends Predicate<Screen> {
    String asString();
    String shortName();

    final class Builder {
        private final List<ScreenPredicate> chain;
        private final String name;

        private static List<ScreenPredicate> concat(List<ScreenPredicate> list, ScreenPredicate next) {
            List<ScreenPredicate> copy = new ArrayList<>(list.size() + 1);
            copy.addAll(list);
            copy.add(next);
            return List.copyOf(copy);
        }

        private Builder(List<ScreenPredicate> chain, String name) {
            this.chain = List.copyOf(chain);
            this.name  = name;
        }

        public Builder() {
            this(List.of(), null);
        }

        public Builder name(String label) {
            return new Builder(chain, label);
        }

        public Builder genericContainer() {
            return new Builder(concat(chain, new ScreenPredicate("GenericContainer",
                    screen -> screen instanceof ContainerScreen)), name);
        }

        public Builder containerTitle(String fragment) {
            return new Builder(concat(chain, new ScreenPredicate("Title[" + fragment + "]", screen -> {
                Component text = screen.getTitle();
                return text != null && Util.removeFormatting(text.getString()).contains(fragment);
            })), name);
        }

        public Builder containerItem(MinMaxBounds.Ints slotRange, Item... wanted) {
            String desc = "Item[slots=" + slotRange.min().orElse(0) + ".." +
                    slotRange.max().orElse(54) + ", types=" +
                    java.util.Arrays.toString(wanted) + "]";

            return new Builder(concat(chain, new ScreenPredicate(desc, screen -> ContainerQuery
                    .range(
                            slotRange.min().orElse(0),
                            slotRange.max().orElse(ContainerManager.getLowerChestInventory().getContainerSize() - 1)
                    )
                    .itemType(wanted)
                    .first()
                    .isPresent())), name);
        }

        public Builder containerItem(int slot, Item... wanted) {
            return containerItem(MinMaxBounds.Ints.exactly(slot), wanted);
        }

        public Builder containerQuery(ContainerQuery query) {
            return new Builder(concat(chain, new ScreenPredicate(
                    "Query[" + query.describe() + "]",
                    screen -> query.first().isPresent())), name);
        }

        public Builder containerQuery(Function<Container, ContainerQuery> builder) {
            return containerQuery("fn", builder);
        }

        public Builder containerQuery(String label, Function<Container, ContainerQuery> builder) {
            return new Builder(concat(chain, new ScreenPredicate(
                    "Query[" + label + "]",
                    screen -> builder.apply(ContainerManager.getLowerChestInventory()).first().isPresent())), name);
        }

        public Builder custom(String label, Predicate<Screen> test) {
            return new Builder(concat(chain, new ScreenPredicate(label, test)), name);
        }

        public Builder custom(Predicate<Screen> test) {
            return custom("Custom", test);
        }

        public ScreenType build() {
            String chainDesc = chain.isEmpty()
                    ? "always false"
                    : chain.stream()
                    .map(ScreenPredicate::name)
                    .reduce((a, b) -> a + " && " + b)
                    .orElse("");

            String label = name != null
                    ? name + " (" + chainDesc + ")"
                    : chainDesc;

            return new ScreenType() {
                @Override
                public String asString() {
                    return label;
                }

                public String shortName() {
                    return name != null ? name : label;
                }

                @Override
                public boolean test(Screen screen) {
                    return chain.stream().allMatch(predicate -> predicate.test(screen));
                }

                @Override
                public String toString() {
                    return asString();
                }
            };
        }

        private record ScreenPredicate(String name, Predicate<Screen> predicate) implements Predicate<Screen> {
            @Override
            public boolean test(Screen screen) {
                return predicate.test(screen);
            }
        }
    }
}