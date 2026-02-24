package com.github.mkram17.bazaarutils.config.util.api;

import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigObjectEntryElement;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigObjectEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Typed wrapper around RC's parsed {@link ResourcefulConfigObjectEntryElement} for
 * {@link SerializableList} fields.
 *
 * <p>Carries the {@link ResourcefulConfig} reference so the renderer doesn't need it
 * injected separately, and exposes typed accessors for title, description, list instance,
 * and save wiring — keeping the renderer a pure UI layer.
 */
public final class SerializableListElement implements ResourcefulConfigObjectEntryElement {

    private final ResourcefulConfigObjectEntryElement delegate;
    private final ResourcefulConfig config;

    private SerializableListElement(ResourcefulConfigObjectEntryElement delegate, ResourcefulConfig config) {
        this.delegate = delegate;
        this.config   = config;
    }

    /**
     * Wraps a raw RC element. Returns {@code null} if the element isn't a
     * {@link ResourcefulConfigObjectEntryElement} backed by a {@link SerializableList}.
     */
    public static SerializableListElement wrap(ResourcefulConfigElement raw, ResourcefulConfig config) {
        if (raw instanceof ResourcefulConfigObjectEntryElement oee
                && oee.entry().instance() instanceof SerializableList<?>) {
            return new SerializableListElement(oee, config);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    //  Typed accessors for the renderer
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public <T> SerializableList<T> list() {
        return (SerializableList<T>) delegate.entry().instance();
    }

    public ResourcefulConfig config() {
        return config;
    }

    public Text title() {
        Field f = entryField();

        if (f != null) {
            ConfigEntry ann = f.getAnnotation(ConfigEntry.class);
            if (ann != null && !ann.translation().isEmpty()) {
                return Text.translatable(ann.translation());
            }
        }

        return Text.literal(delegate.id());
    }

    public Text description() {
        Field f = entryField();
        if (f != null) {
            Comment comment = f.getAnnotation(Comment.class);
            if (comment != null) {
                return !comment.translation().isEmpty()
                        ? Text.translatable(comment.translation())
                        : Text.literal(comment.value());
            }
        }
        return Text.empty();
    }

    // -------------------------------------------------------------------------
    //  ResourcefulConfigObjectEntryElement delegation
    // -------------------------------------------------------------------------

    @Override public ResourcefulConfigObjectEntry entry()              { return delegate.entry(); }
    @Override public String id()                                       { return delegate.id(); }
    @Override public Identifier renderer()                             { return delegate.renderer(); }
    @Override public boolean isHidden()                                { return delegate.isHidden(); }
    @Override public boolean search(Predicate<String> predicate)       { return delegate.search(predicate); }

    // -------------------------------------------------------------------------
    //  Internal helper
    // -------------------------------------------------------------------------

    private Field entryField() {
        try {
            Method m = delegate.entry().getClass().getDeclaredMethod("field");
            m.setAccessible(true);
            return (Field) m.invoke(delegate.entry());
        } catch (Exception ignored) {
            return null;
        }
    }
}