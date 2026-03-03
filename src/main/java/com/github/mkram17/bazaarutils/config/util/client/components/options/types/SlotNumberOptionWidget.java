package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.github.mkram17.bazaarutils.config.util.api.SlotElement;
import com.teamresourceful.resourcefulconfig.client.components.options.types.NumberOptionWidget;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ResetableWidget;

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