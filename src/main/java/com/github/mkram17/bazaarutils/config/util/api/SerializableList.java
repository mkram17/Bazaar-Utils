package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.config.util.RCInternals;
import com.github.mkram17.bazaarutils.config.util.client.SerializableListRenderer;
import com.github.mkram17.bazaarutils.config.util.client.SerializableListScreen;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigObjectEntry;
import com.teamresourceful.resourcefulconfig.api.types.entries.SerializableObject;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A config-serialisable list of any {@code @ConfigObject}-annotated type.
 *
 * <p>Declare it in your config class like any other {@code @ConfigObject} field:
 * <pre>
 *   {@literal @}ConfigEntry(id = "myList")
 *   {@literal @}ConfigOption.Renderer("yourmod:serializable_list")
 *   public static final SerializableList<MyEntry> myList = new SerializableList<>(MyEntry::new);
 * </pre>
 *
 * Where {@code MyEntry} is simply:
 * <pre>
 *   {@literal @}ConfigObject
 *   public class MyEntry {
 *       {@literal @}ConfigEntry(id = "name") public String name = "default";
 *       {@literal @}ConfigEntry(id = "value") public int value = 0;
 *   }
 * </pre>
 */
@ConfigObject
public class SerializableList<T> implements SerializableObject {

    public final List<T> entries = new ArrayList<>();
    private final Supplier<T> factory;

    /**
     * Called by {@link SerializableListRenderer} to wire up disk persistence.
     * Invoked after every add, remove, or edit-screen close.
     * -- SETTER --
     * Wired once by
     *  when the widget is built.

     */
    @Setter
    private Runnable saveCallback = () -> {};

    public SerializableList(Supplier<T> factory) {
        this.factory = factory;
    }

    /**
     * Triggers the save callback. Called from {@link SerializableListScreen}
     * on every structural change and when the screen closes.
     */
    public void requestSave() {
        saveCallback.run();
    }

    /** Creates a blank entry via the factory. Used by the screen's Add button. */
    public T newEntry() {
        return factory.get();
    }

    // -------------------------------------------------------------------------
    //  SerializableObject — RC calls these when saving/loading the config file
    // -------------------------------------------------------------------------

    @Override
    public JsonElement save() {
        JsonArray array = new JsonArray();
        for (T item : entries) {
            ResourcefulConfigObjectEntry objectEntry = RCInternals.buildObjectEntry(item);
            if (objectEntry == null) continue;
            JsonElement serialised = RCInternals.saveObjectEntry(objectEntry);
            if (serialised != null) {
                array.add(serialised);
            }
        }
        return array;
    }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonArray()) return;
        entries.clear();
        for (JsonElement element : json.getAsJsonArray()) {
            if (!(element instanceof JsonObject jsonObject)) continue;
            T item = factory.get();
            ResourcefulConfigObjectEntry objectEntry = RCInternals.buildObjectEntry(item);
            if (objectEntry == null) continue;
            RCInternals.loadObjectEntry(objectEntry, jsonObject);
            entries.add(item);
        }
    }
}