package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.api.SlotElement;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.teamresourceful.resourcefulconfig.client.components.options.types.NumberOptionWidget;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ResetableWidget;
import net.minecraft.item.ItemStack;

public class SlotNumberOptionWidget extends NumberOptionWidget<Integer> implements ResetableWidget {
    private final int maxSlot;

    public SlotNumberOptionWidget(SlotElement element) {
        super(
                element.valueEntry()::getInt,
                value -> {
                    element.valueEntry().setInt(value);
                    return true;
                },
                s -> {
                    int value = Integer.parseInt(s);
                    int max   = element.totalSlots() - 1;
                    if (value < 0 || value > max) throw new NumberFormatException();
                    ItemStack stack = element.provider().getStack(value);
                    // Known undeseriable/bug behavior:
                    // to throw NumberFormatException() will cause the input box to fallback to the last typed-in value,
                    // not the last saved/valid value. This is a implementation detail of NumberOptionWidget,
                    // and although we may override and fix it with a custom setChangedListener(...) call,
                    // it'd be better off if upstream fixes this behavior. 
                    if (!stack.isEmpty() && stack.contains(CustomDataComponents.SLOT_SELECTOR_LOCKED)) throw new NumberFormatException();
                    return value;
                },
                NumberOptionWidget.INTEGER_FILTER
        );
        this.maxSlot = element.totalSlots() - 1;
    }

    private static final boolean canExpand = false;

    @Override
    public void updateIfFocused() {
        // if (!canExpand) return;
        // rather than to do an unless check, we just no-op, as it should never expand.
    }
}