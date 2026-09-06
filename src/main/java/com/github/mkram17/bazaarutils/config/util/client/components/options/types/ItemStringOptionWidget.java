package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.utils.minecraft.item.ItemsRepo;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ResetableWidget;
import com.teamresourceful.resourcefulconfig.client.components.options.types.StringOptionWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class ItemStringOptionWidget extends StringOptionWidget implements ResetableWidget {
    private final Supplier<String> getter;

    private String lastResolvedId;
    private String lastResolvedName;

    public ItemStringOptionWidget(Supplier<String> getter, Function<String, Boolean> setter) {
        super(getter, setter, false);
        this.getter = getter;
    }

    @Override
    public void updateIfFocused() {
        if (!isFocused()) {
            String id = getter.get();

            if (!Objects.equals(id, lastResolvedId)) {
                lastResolvedId = id;

                ItemStack stack = ItemsRepo.resolve(id);
                lastResolvedName = stack != null
                        ? ChatFormatting.stripFormatting(stack.getHoverName().getString())
                        : id;
            }

            setValue(lastResolvedName);
            setCursorPosition(0);
            setHighlightPos(0);
            setTextColor(0xFFE0E0E0);
        }
    }

    @Override
    public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();

        super.setFocused(focused);

        if (focused && !wasFocused) {
            setValue(getter.get());
            setCursorPosition(getValue().length());
            setHighlightPos(getValue().length());
        }
    }

    @Override
    public void reset() {
        setValue(getter.get());
    }
}