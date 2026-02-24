package com.github.mkram17.bazaarutils.config.util;

import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigEntryElement;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigEntry;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigObjectEntry;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigValueEntry;
import com.teamresourceful.resourcefulconfig.api.types.options.EntryData;
import com.teamresourceful.resourcefulconfig.api.types.options.EntryType;
import com.teamresourceful.resourcefulconfig.common.config.ParsingUtils;
import com.teamresourceful.resourcefulconfig.common.loader.Loader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Reflection wrappers around RC's internal classes, statically cached.
 *
 * <p>Key design decision: we do NOT call {@code JavaConfigParser.populateEntries}
 * because {@code ParsedInstanceEntry} is a record — its {@code defaultValue} component
 * is final and cannot be patched after construction. Instead, we replicate the
 * population logic ourselves using the canonical 5-arg constructor, passing the
 * correct {@code defaultValue} (sourced from a fresh factory instance) up front.
 */
public final class RCInternals {

    // ---- ParsedObjectEntry(Field) ----
    private static final Class<?>       PARSED_OBJECT_ENTRY_CLASS;
    private static final Constructor<?> PARSED_OBJECT_ENTRY_CTOR;

    // ---- ParsedInstanceEntry(EntryType, Field, EntryData, Object, Object) ----
    // Canonical record constructor — lets us supply defaultValue explicitly.
    private static final Constructor<?> PARSED_INSTANCE_ENTRY_CTOR;

    // ---- ParsedEntryElement(String, ResourcefulConfigValueEntry) ----
    private static final Constructor<?> PARSED_ENTRY_ELEMENT_CTOR;

    // ---- Loader.loadObject(ResourcefulConfigObjectEntry, JsonObject) ----
    private static final Method LOAD_OBJECT;

    static {
        try {
            PARSED_OBJECT_ENTRY_CLASS = Class.forName(
                    "com.teamresourceful.resourcefulconfig.common.loader.entries.ParsedObjectEntry"
            );
            PARSED_OBJECT_ENTRY_CTOR = PARSED_OBJECT_ENTRY_CLASS.getDeclaredConstructor(Field.class);
            PARSED_OBJECT_ENTRY_CTOR.setAccessible(true);

            Class<?> parsedInstanceEntryClass = Class.forName(
                    "com.teamresourceful.resourcefulconfig.common.loader.entries.ParsedInstanceEntry"
            );
            // Canonical 5-arg record constructor
            PARSED_INSTANCE_ENTRY_CTOR = parsedInstanceEntryClass.getDeclaredConstructor(
                    EntryType.class, Field.class, EntryData.class, Object.class, Object.class
            );
            PARSED_INSTANCE_ENTRY_CTOR.setAccessible(true);

            Class<?> parsedEntryElementClass = Class.forName(
                    "com.teamresourceful.resourcefulconfig.common.loader.elements.ParsedEntryElement"
            );
            PARSED_ENTRY_ELEMENT_CTOR = parsedEntryElementClass.getDeclaredConstructor(
                    String.class, ResourcefulConfigEntry.class
            );
            PARSED_ENTRY_ELEMENT_CTOR.setAccessible(true);

            LOAD_OBJECT = Loader.class.getDeclaredMethod(
                    "loadObject", ResourcefulConfigObjectEntry.class, JsonObject.class
            );
            LOAD_OBJECT.setAccessible(true);

        } catch (Exception e) {
            throw new RuntimeException(
                    "[RCInternals] Failed to cache reflective targets — RC internal structure may have changed.", e
            );
        }
    }

    private RCInternals() {}

    // -------------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------------

    /**
     * Builds a fully populated {@link ResourcefulConfigObjectEntry} for {@code liveInstance},
     * with reset-defaults sourced from {@code defaultInstance} (a fresh factory copy).
     *
     * <p>We bypass {@code JavaConfigParser.populateEntries} entirely because
     * {@code ParsedInstanceEntry} is a record — its {@code defaultValue} component is
     * final and cannot be changed after construction. Instead, we build each
     * {@code ParsedInstanceEntry} using the canonical 5-arg constructor, explicitly
     * passing {@code field.get(defaultInstance)} as the last argument so that
     * RC's Reset button always restores to the class's field initializer values.
     *
     * @param liveInstance    the entry whose fields will be read/written by the UI
     * @param defaultInstance a fresh factory instance whose field values are the true
     *                        defaults; pass {@code null} to use liveInstance values as defaults
     */
    public static ResourcefulConfigObjectEntry buildObjectEntry(Object liveInstance, Object defaultInstance) {
        try {
            Field metaField = firstConfigEntryField(liveInstance.getClass());
            if (metaField == null) return null;

            Object parsedObjectEntry = PARSED_OBJECT_ENTRY_CTOR.newInstance(metaField);
            populateManually(liveInstance, defaultInstance, (ResourcefulConfigObjectEntry) parsedObjectEntry);
            return (ResourcefulConfigObjectEntry) parsedObjectEntry;

        } catch (Exception e) {
            System.err.println("[RCInternals] buildObjectEntry failed for "
                    + liveInstance.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Convenience overload — uses liveInstance values as defaults (no reset support). */
    public static ResourcefulConfigObjectEntry buildObjectEntry(Object liveInstance) {
        return buildObjectEntry(liveInstance, null);
    }

    public static JsonObject saveObjectEntry(ResourcefulConfigObjectEntry entry) {
        try {
            JsonObject obj = new JsonObject();
            for (ResourcefulConfigElement element : entry.elements()) {
                if (!(element instanceof ResourcefulConfigEntryElement entryElement)) continue;
                if (!(entryElement.entry() instanceof ResourcefulConfigValueEntry valueEntry)) continue;
                Object value = valueEntry.get();
                if (value == null) continue;
                obj.add(entryElement.id(), toJsonElement(value));
            }
            return obj;
        } catch (Exception e) {
            System.err.println("[RCInternals] saveObjectEntry failed: " + e.getMessage());
            return null;
        }
    }

    private static JsonElement toJsonElement(Object value) {
        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            ParsingUtils.forEach(value, o -> array.add(toJsonElement(o)));
            return array;
        }
        return switch (value) {
            case Boolean b -> new JsonPrimitive(b);
            case Number  n -> new JsonPrimitive(n);
            case String  s -> new JsonPrimitive(s);
            case Enum<?> e -> new JsonPrimitive(e.name());
            default        -> throw new IllegalArgumentException(
                    "[RCInternals] Unsupported value type: " + value.getClass());
        };
    }

    /**
     * Deserialises a {@link JsonObject} into an entry instance via RC's own
     * {@code Loader.loadObject}.
     */
    public static void loadObjectEntry(ResourcefulConfigObjectEntry entry, JsonObject json) {
        try {
            LOAD_OBJECT.invoke(null, entry, json);
        } catch (Exception e) {
            System.err.println("[RCInternals] loadObjectEntry failed: " + e.getMessage());
        }
    }

    /** Flushes the config to disk. */
    public static void writeConfig(ResourcefulConfig config) {
        config.save();
    }

    /**
     * Resets all {@code @ConfigEntry} fields on {@code liveInstance} to the values
     * from {@code defaultInstance} (a fresh factory copy).
     */
    public static void resetEntryToDefaults(Object liveInstance, Object defaultInstance) {
        for (Field field : liveInstance.getClass().getDeclaredFields()) {
            if (field.getAnnotation(ConfigEntry.class) == null) continue;
            try {
                field.setAccessible(true);
                field.set(liveInstance, field.get(defaultInstance));
            } catch (Exception e) {
                System.err.println("[RCInternals] resetEntryToDefaults failed for field '"
                        + field.getName() + "': " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    //  Manual population — replicates populateEntries with explicit defaultValue
    // -------------------------------------------------------------------------

    /**
     * Iterates all {@code @ConfigEntry} fields on {@code liveInstance}'s class and
     * adds a {@code ParsedInstanceEntry} + {@code ParsedEntryElement} to {@code entry}
     * for each one, using the canonical 5-arg constructor so we can supply
     * {@code defaultValue} from {@code defaultInstance} directly.
     */
    @SuppressWarnings("unchecked")
    private static void populateManually(
            Object liveInstance,
            Object defaultInstance,
            ResourcefulConfigObjectEntry entry
    ) throws Exception {
        for (Field field : liveInstance.getClass().getDeclaredFields()) {
            ConfigEntry configEntry = field.getAnnotation(ConfigEntry.class);
            if (configEntry == null) continue;

            field.setAccessible(true);

            EntryType type = entryTypeOf(field.getType());
            if (type == EntryType.OBJECT) {
                // Nested @ConfigObject inside a list entry is not supported by RC either
                System.err.println("[RCInternals] Skipping nested @ConfigObject field: " + field.getName());
                continue;
            }

            EntryData entryData = EntryData.of(field, field.getType());

            Object defaultValue = defaultInstance != null
                    ? field.get(defaultInstance)
                    : field.get(liveInstance);

            Object parsedInstanceEntry = PARSED_INSTANCE_ENTRY_CTOR.newInstance(
                    type, field, entryData, liveInstance, defaultValue
            );

            Object parsedEntryElement = PARSED_ENTRY_ELEMENT_CTOR.newInstance(
                    configEntry.id(), (ResourcefulConfigEntry) parsedInstanceEntry
            );

            ((List<ResourcefulConfigElement>) entry.elements()).add(
                    (ResourcefulConfigElement) parsedEntryElement
            );
        }
    }

    // -------------------------------------------------------------------------
    //  EntryType resolution — mirrors JavaConfigParser.getEntryType(Class<?>)
    // -------------------------------------------------------------------------

    private static EntryType entryTypeOf(Class<?> type) {
        // Unwrap arrays — EntryType is based on component type
        if (type.isArray()) type = type.getComponentType();

        if (type.isAnnotationPresent(ConfigObject.class)) return EntryType.OBJECT;
        if (type == long.class    || type == Long.class)    return EntryType.LONG;
        if (type == int.class     || type == Integer.class) return EntryType.INTEGER;
        if (type == short.class   || type == Short.class)   return EntryType.SHORT;
        if (type == byte.class    || type == Byte.class)    return EntryType.BYTE;
        if (type == double.class  || type == Double.class)  return EntryType.DOUBLE;
        if (type == float.class   || type == Float.class)   return EntryType.FLOAT;
        if (type == boolean.class || type == Boolean.class) return EntryType.BOOLEAN;
        if (type == String.class)                           return EntryType.STRING;
        if (type.isEnum())                                  return EntryType.ENUM;
        throw new IllegalArgumentException("[RCInternals] Unsupported field type: " + type);
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private static Field firstConfigEntryField(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConfigEntry.class)) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }
}