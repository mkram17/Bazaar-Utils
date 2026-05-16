package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerQuery;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.advancements.criterion.MinMaxBounds;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

public interface ScreenType extends Predicate<Screen> {
    String name();

    /**
     * Returns true if this type semantically covers {@code other}.
     *
     * <p>The default is identity: a type covers only itself. Subtypes that
     * represent a structural group (e.g. an eager {@code BazaarScreenType})
     * can override to cover all concrete members of that group.
     */
    default boolean includes(ScreenType other) {
        return this == other;
    }

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

    static Predicate<Screen> isSign() {
        return screen -> screen instanceof AbstractSignEditScreen;
    }

    static Predicate<Screen> hasPreviousScreen(ScreenType wanted) {
        // Runs at match time, albeit whereof the to-be-computed entry hasnt been yet inserted to the screen,
        // alas that the .current() screen for history is the previous to the one we're computing.
        return screen -> ScreenManager.getInstance().current()
                .map(ctx -> ctx.is(wanted))
                .orElse(false);
    }

    static Predicate<Screen> hasTitle(String fragment, String... exclude) {
        return screen -> {
            String title = Util.removeFormatting(screen.getTitle().getString());

            if (!title.contains(fragment)) return false;

            for (String excluded : exclude) {
                if (title.contains(excluded)) return false;
            }

            return true;
        };
    }

    /** Some containers have their name built from a product/items' name, which if too long would be naturally truncated by Minecraft */
    static Predicate<Screen> isTruncatedTitle() {
        return screen -> Util.removeFormatting(
                screen.getTitle().getString()
        ).length() >= 30;
    }

    static Predicate<Screen> hasSignLine(int line, String content) {
        return screen -> screen instanceof AbstractSignEditScreen sign
                && Util.removeFormatting(
                ((AccessorSignEditScreen) sign).getMessages()[line]
        ).equals(content);
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
}