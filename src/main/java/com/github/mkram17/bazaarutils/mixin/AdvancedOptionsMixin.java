package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.config.util.SeparatorFieldStore;
import com.github.mkram17.bazaarutils.config.util.api.annotations.ShowIf;
import com.github.mkram17.bazaarutils.config.util.api.conditions.ConfigCondition;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigEntryElement;
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigSeparatorElement;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigFieldBackedValueEntry;
import com.teamresourceful.resourcefulconfig.client.components.options.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.Optional;

@Mixin(value = Options.class, remap = false)
public class AdvancedOptionsMixin {
    @Unique
    private static final LoadingCache<Class<? extends ConfigCondition>, ConfigCondition> CONDITION_CACHE =
            CacheBuilder.newBuilder().build(CacheLoader.from(cls -> {
                try {
                    return cls.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    return owner -> true;
                }
            }));

    @Redirect(
            method = "populateOptions",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/teamresourceful/resourcefulconfig/api/types/ResourcefulConfigElement;isHidden()Z"
            )
    )
    private static boolean checkHidden(ResourcefulConfigElement element) {
        if (element.isHidden()) return true;

        if (element instanceof ResourcefulConfigSeparatorElement sep) {
            return SeparatorFieldStore.get(sep)
                    .filter(ctx -> ctx.field().isAnnotationPresent(ShowIf.class))
                    .map(ctx -> hiddenByCondition(ctx.field(), ctx.owner()))
                    .orElse(false);
        }

        if (!(element instanceof ResourcefulConfigEntryElement entry)) return false;
        if (!(entry.entry() instanceof ResourcefulConfigFieldBackedValueEntry backed)) return false;

        return hiddenByCondition(backed.field(), Optional.ofNullable(backed.instance()));
    }

    @Unique
    private static boolean hiddenByCondition(Field field, Optional<Object> owner) {
        ShowIf ann = field.getAnnotation(ShowIf.class);
        if (ann == null) return false;

        for (Class<? extends ConfigCondition> cls : ann.value()) {
            if (!CONDITION_CACHE.getUnchecked(cls).shouldShow(owner)) return true;
        }

        return false;
    }
}