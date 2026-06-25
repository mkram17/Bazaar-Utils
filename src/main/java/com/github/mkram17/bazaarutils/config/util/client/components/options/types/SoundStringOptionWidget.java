package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.teamresourceful.resourcefulconfig.client.components.options.types.ResetableWidget;
import com.teamresourceful.resourcefulconfig.client.components.options.types.StringOptionWidget;

import java.util.function.Function;
import java.util.function.Supplier;

public class SoundStringOptionWidget extends StringOptionWidget implements ResetableWidget {
    private final Supplier<String> getter;

    public SoundStringOptionWidget(Supplier<String> getter, Function<String, Boolean> setter) {
        super(getter, setter, false);
        this.getter = getter;
    }

    @Override
    public void reset() {
        setValue(getter.get());
    }
}