package com.github.mkram17.bazaarutils.utils.minecraft.components;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

@PreInitModule
public final class CustomDataComponents {
    public static ComponentType<String> CUSTOM_SIZE;
    public static ComponentType<Boolean> SHOW_PRICE_CHART;
    public static ComponentType<Boolean> SLOT_SELECTOR_LOCKED;

    public CustomDataComponents() {
        CUSTOM_SIZE = register("custom_size", ComponentType.<String>builder().codec(Codec.STRING).build());
        SHOW_PRICE_CHART = register("has_price_chart", ComponentType.<Boolean>builder().codec(Codec.BOOL).build());
        SLOT_SELECTOR_LOCKED = register("slot_selector_locked", ComponentType.<Boolean>builder().codec(Codec.BOOL).build());
    }

    private static <T> ComponentType<T> register(String id, ComponentType<T> type) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(BazaarUtils.MOD_ID, id), type);
    }
}