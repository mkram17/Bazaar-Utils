package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.config.util.api.annotations.ShowIf;
import com.github.mkram17.bazaarutils.config.util.api.conditions.ConfigCondition;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigEntryElement;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigFieldBackedValueEntry;
import com.teamresourceful.resourcefulconfig.client.components.options.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(value = Options.class, remap = false)
public class AdvancedOptionsMixin {
    @Unique
    private static final LoadingCache<Class<? extends ConfigCondition>, ConfigCondition> CONDITION_CACHE =
            CacheBuilder.newBuilder().build(CacheLoader.from(it -> {
                try {
                    return it.getDeclaredConstructor().newInstance();
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
        if (!(element instanceof ResourcefulConfigEntryElement entry)) return false;
        if (!(entry.entry() instanceof ResourcefulConfigFieldBackedValueEntry backed)) return false;

        ShowIf ann = backed.field().getAnnotation(ShowIf.class);
        if (ann == null) return false;

        // Optional.ofNullable makes the static-field case (null instance) explicit at
        // every condition call site, rather than relying on @Nullable conventions.
        Optional<Object> instance = Optional.ofNullable(backed.instance());

        for (Class<? extends ConfigCondition> cls : ann.value()) {
            if (!CONDITION_CACHE.getUnchecked(cls).shouldShow(instance)) return true;
        }

        return false;
    }
}