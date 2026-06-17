package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.config.util.SeparatorFieldStore;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;
import com.teamresourceful.resourcefulconfig.common.loader.JavaConfigParser;
import com.teamresourceful.resourcefulconfig.common.loader.elements.ParsedSeparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

@Mixin(value = JavaConfigParser.class, remap = false)
public class JavaConfigParserMixin {

    /**
     * Holds the current {@code @ConfigObject} owner instance for the duration of
     * {@code populateObjectEntryElements}. Empty outside that scope (top-level static fields).
     */
    @Unique
    private static final ThreadLocal<Object> OBJECT_SCOPE = new ThreadLocal<>();

    @Inject(method = "populateObjectEntryElements", at = @At("HEAD"))
    private static void enterScope(Object instance, List<ResourcefulConfigElement> elements, CallbackInfo ci) {
        OBJECT_SCOPE.set(instance);
    }

    @Inject(method = "populateObjectEntryElements", at = @At("RETURN"))
    private static void exitScope(Object instance, List<ResourcefulConfigElement> elements, CallbackInfo ci) {
        OBJECT_SCOPE.remove();
    }

    /**
     * Intercepts {@code ParsedSeparator.of} inside {@code populateObjectEntryElements}
     * (instance fields of a {@code @ConfigObject}). Owner comes from {@link #OBJECT_SCOPE}.
     */
    @Redirect(
            method = "populateObjectEntryElements",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/teamresourceful/resourcefulconfig/common/loader/elements/ParsedSeparator;of(Ljava/lang/reflect/Field;)Lcom/teamresourceful/resourcefulconfig/common/loader/elements/ParsedSeparator;"
            )
    )
    private static ParsedSeparator captureObjectSeparator(Field field) {
        ParsedSeparator separator = ParsedSeparator.of(field);

        SeparatorFieldStore.put(separator, field, Optional.ofNullable(OBJECT_SCOPE.get()));

        return separator;
    }

    /**
     * Intercepts {@code ParsedSeparator.of} inside the top-level/category
     * {@code populateEntries}. These are always static config fields — owner is empty.
     */
    @Redirect(
            method = "populateEntries(Ljava/lang/Class;Lcom/teamresourceful/resourcefulconfig/api/types/ResourcefulConfig;[Ljava/lang/Class;)Lcom/teamresourceful/resourcefulconfig/api/types/ResourcefulConfig;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/teamresourceful/resourcefulconfig/common/loader/elements/ParsedSeparator;of(Ljava/lang/reflect/Field;)Lcom/teamresourceful/resourcefulconfig/common/loader/elements/ParsedSeparator;"
            )
    )
    private static ParsedSeparator captureStaticSeparator(Field field) {
        ParsedSeparator separator = ParsedSeparator.of(field);

        SeparatorFieldStore.put(separator, field, Optional.empty());

        return separator;
    }
}