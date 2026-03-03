package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.api.ResourcefulConfigItems;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ResetableWidget;
import com.teamresourceful.resourcefulconfig.client.components.options.types.StringOptionWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.function.Function;
import java.util.function.Supplier;

public class ItemStringOptionWidget extends StringOptionWidget implements ResetableWidget {
    private final Supplier<String> getter;

    public ItemStringOptionWidget(Supplier<String> getter, Function<String, Boolean> setter) {
        super(getter, setter, false);
        this.getter = getter;
    }

    @Override
    public void updateIfFocused() {
        if (!isFocused()) {
            Identifier id = Identifier.tryParse(getter.get());

            Item resolved = id != null
                    ? ResourcefulConfigItems.getItems().stream()
                    .filter(item -> Registries.ITEM.getId(item).equals(id))
                    .findFirst()
                    .orElse(null)
                    : null;

            setValue(resolved != null ? resolved.getName(new ItemStack(resolved)).getString() : getter.get());
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