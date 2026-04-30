package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.advancements.criterion.MinMaxBounds;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

public interface ScreenType extends Predicate<Screen> {
    String name();

    static ScreenType named(String name, Predicate<Screen> test) {
        return new ScreenType() {
            public String name() {
                return name;
            }

            public boolean test(Screen screen) {
                return test.test(screen);
            }

            public String toString() {
                return name;
            }
        };
    }

    default @NotNull ScreenType and(@NotNull Predicate<? super Screen> other) {
        return named(name(), s -> test(s) && other.test(s));
    }

    static Predicate<Screen> isContainer() {
        return screen -> screen instanceof AbstractContainerScreen<?>;
    }

    static Predicate<Screen> hasTitle(String fragment) {
        return screen -> Util.removeFormatting(screen.getTitle().getString()).contains(fragment);
    }

    static Predicate<Screen> hasItem(MinMaxBounds.Ints slotRange, Item... wanted) {
        return screen -> screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof ChestMenu chest
                && ContainerQuery.range(
                        slotRange.min().orElse(0),
                        slotRange.max().orElse(chest.getContainer().getContainerSize() - 1)
                )
                .itemType(wanted)
                .first(chest.getContainer())
                .isPresent();
    }

    static Predicate<Screen> hasItem(int slot, Item... wanted) {
        return hasItem(MinMaxBounds.Ints.exactly(slot), wanted);
    }

    static Predicate<Screen> hasSlot(ContainerQuery query) {
        return screen -> screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof ChestMenu chest
                && query.first(chest.getContainer()).isPresent();
    }

    static Predicate<Screen> hasSlot(Function<Container, ContainerQuery> query) {
        return screen -> screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof ChestMenu chest
                && query.apply(chest.getContainer()).first(chest.getContainer()).isPresent();
    }

    static Predicate<Screen> hasSlot(String label, Function<Container, ContainerQuery> query) {
        return hasSlot(query);
    }
}