package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.advancements.criterion.MinMaxBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface ScreenType extends Predicate<Screen> {
    String name();

    final class Builder {
        private static final BazaarLogger LOG = BazaarLogger.of(ContainerQuery.class);

        private final String name;
        private final List<ScreenPredicate> chain;

        private static List<ScreenPredicate> concat(List<ScreenPredicate> list, ScreenPredicate next) {
            List<ScreenPredicate> copy = new ArrayList<>(list.size() + 1);

            copy.addAll(list);
            copy.add(next);

            return List.copyOf(copy);
        }

        private Builder(List<ScreenPredicate> chain, String name) {
            this.chain = List.copyOf(chain);
            this.name = name;
        }

        public Builder() {
            this(List.of(), null);
        }

        public Builder name(String label) {
            return new Builder(chain, label);
        }

        public Builder genericContainer() {
            return new Builder(concat(chain, new ScreenPredicate("GenericContainer", screen -> screen instanceof ContainerScreen)), name);
        }

        public Builder containerTitle(String fragment) {
            return new Builder(concat(chain, new ScreenPredicate("ContainerTitle", screen -> Util.removeFormatting(screen.getTitle().getString()).contains(fragment))), name);
        }

        public Builder containerItem(MinMaxBounds.Ints slotRange, Item... wanted) {
            return new Builder(concat(chain, new ScreenPredicate("ContainerItem", screen -> screen instanceof AbstractContainerScreen<?> container
                    && container.getMenu() instanceof ChestMenu chest
                    && ContainerQuery.range(
                            slotRange.min().orElse(0),
                            slotRange.max().orElse(chest.getContainer().getContainerSize() - 1)
                    )
                    .itemType(wanted)
                    .first()
                    .isPresent())), name);
        }

        public Builder containerItem(int slot, Item... wanted) {
            return containerItem(MinMaxBounds.Ints.exactly(slot), wanted);
        }

        public Builder containerQuery(ContainerQuery query) {
            return new Builder(concat(chain, new ScreenPredicate("ContainerQuery", screen -> query.first().isPresent())), name);
        }

        public Builder containerQuery(Function<Container, ContainerQuery> builder) {
            return new Builder(concat(chain, new ScreenPredicate("ContainerQuery", screen -> screen instanceof AbstractContainerScreen<?> container
                    && container.getMenu() instanceof ChestMenu chest
                    && builder.apply(chest.getContainer()).first().isPresent())), name);
        }

        public Builder containerQuery(String label, Function<Container, ContainerQuery> builder) {
            return new Builder(concat(chain, new ScreenPredicate("ContainerQuery[%s]".formatted(label), screen -> screen instanceof AbstractContainerScreen<?> container
                    && container.getMenu() instanceof ChestMenu chest
                    && builder.apply(chest.getContainer()).first().isPresent())), name);
        }

        public ScreenType build() {
            return new ScreenType() {
                public String name() {
                    return name != null ? name : "???";
                }

                @Override
                public boolean test(Screen screen) {
                    boolean result = chain.stream().allMatch(predicate -> predicate.test(screen));

                    LOG.debug("ScreenType[%s] → %b".formatted(name, result));

                    return result;
                }

                @Override
                public String toString() {
                    return name();
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