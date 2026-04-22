package com.github.mkram17.bazaarutils.utils.minecraft.components;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.annotations.modules.PreInitModule;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

@PreInitModule
public final class CustomDataComponents {
    private static final BazaarLogger LOG = BazaarLogger.of(CustomDataComponents.class);

    public static DataComponentType<String> CUSTOM_SIZE;
    public static DataComponentType<Boolean> SHOW_PRICE_CHART;
    public static DataComponentType<Boolean> SLOT_SELECTOR_LOCKED;

    public CustomDataComponents() {
        CUSTOM_SIZE = register("custom_size", DataComponentType.<String>builder().persistent(Codec.STRING).build());
        SHOW_PRICE_CHART = register("has_price_chart", DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build());
        SLOT_SELECTOR_LOCKED = register("slot_selector_locked", DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build());

        LOG.info("CustomDataComponents registered");
    }

    private static <T> DataComponentType<T> register(String id, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(BazaarUtils.MOD_ID, id), type);
    }
}