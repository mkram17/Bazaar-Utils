package com.github.mkram17.bazaarutils.config.util.api;

import com.github.mkram17.bazaarutils.config.util.api.annotations.ItemTag;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfigElement;
import com.teamresourceful.resourcefulconfig.api.types.elements.ResourcefulConfigEntryElement;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigEntry;
import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigValueEntry;
import com.teamresourceful.resourcefulconfig.api.types.options.EntryType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

public final class ItemElement implements ResourcefulConfigEntryElement {
    private final ResourcefulConfigEntryElement delegate;

    private final String tag;

    private ItemElement(ResourcefulConfigEntryElement delegate, String tag) {
        this.delegate = delegate;
        this.tag = tag;
    }

    public static ItemElement wrap(ResourcefulConfigElement element) {
        if (!(element instanceof ResourcefulConfigEntryElement entry)) return null;
        if (!(entry.entry() instanceof ResourcefulConfigValueEntry value)) return null;
        if (!(value.type() == EntryType.STRING)) return null;

        Field field = entryField(entry);

        if (field == null) return null;

        String tag = field.isAnnotationPresent(ItemTag.class) ? field.getAnnotation(ItemTag.class).value() : "";

        return new ItemElement(entry, tag);
    }

    private static Field entryField(ResourcefulConfigEntryElement delegate) {
        try {
            Method field = delegate.entry().getClass().getDeclaredMethod("field");

            field.setAccessible(true);

            return (Field) field.invoke(delegate.entry());
        } catch (Exception ignored) {
            return null;
        }
    }

    public String tag() {
        return tag;
    }

    public ResourcefulConfigValueEntry valueEntry() {
        return (ResourcefulConfigValueEntry) delegate.entry();
    }

    public Text title() {
        Field field = entryField();

        if (field != null) {
            ConfigEntry options = field.getAnnotation(ConfigEntry.class);

            if (options != null && !options.translation().isEmpty()) {
                return Text.translatable(options.translation());
            }
        }

        return Text.literal(delegate.id());
    }

    public Text description() {
        Field field = entryField();

        if (field != null) {
            Comment comment = field.getAnnotation(Comment.class);

            if (comment != null) {
                return !comment.translation().isEmpty()
                        ? Text.translatable(comment.translation())
                        : Text.literal(comment.value());
            }
        }

        return Text.empty();
    }

    @Override public ResourcefulConfigEntry entry() {
        return delegate.entry();
    }

    @Override public String id() {
        return delegate.id();
    }

    @Override public Identifier renderer() {
        return delegate.renderer();
    }

    @Override public boolean isHidden() {
        return delegate.isHidden();
    }

    @Override public boolean search(Predicate<String> predicate) {
        return delegate.search(predicate);
    }

    private Field entryField() {
        try {
            Method field = delegate.entry().getClass().getDeclaredMethod("field");

            field.setAccessible(true);

            return (Field) field.invoke(delegate.entry());
        } catch (Exception ignored) {
            return null;
        }
    }
}