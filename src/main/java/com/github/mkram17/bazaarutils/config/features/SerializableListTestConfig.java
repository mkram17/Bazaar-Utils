package com.github.mkram17.bazaarutils.config.features;

import com.github.mkram17.bazaarutils.config.util.api.SerializableList;
import com.github.mkram17.bazaarutils.config.util.api.SerializableListEntrySummaryProvider;
import com.teamresourceful.resourcefulconfig.api.annotations.*;
import net.minecraft.text.Text;

@Category(value = "test_config")
@ConfigInfo(
        title = "Test Config",
        titleTranslation = "bazaarutils.config.test.category.value",
        description = "A Test Configuration for the case of this PR",
        descriptionTranslation = "bazaarutils.config.test.category.description",
        icon = "keyboard"
)
public final class SerializableListTestConfig {
    @ConfigEntry(
            id = "basic",
            translation = "bazaarutils.config.test.basic.value"
    )
    @ConfigOption.Renderer("serializablelist")
    public static final SerializableList<TestObject> BASIC_LIST = new SerializableList<TestObject>(TestObject::new);

    @ConfigEntry(
            id = "commented",
            translation = "bazaarutils.config.test.commented.value"
    )
    @Comment(
            value = "This is a description comment",
            translation = "bazaarutils.config.test.commented.description"
    )
    @ConfigOption.Renderer("serializablelist")
    public static final SerializableList<TestObject> COMMENTED_LIST = new SerializableList<TestObject>(TestObject::new);

    @ConfigObject
    public static final class TestObject implements SerializableListEntrySummaryProvider {
        @ConfigEntry(
                id = "enabled",
                translation = "bazaarutils.config.test:entry.enabled.value"
        )
        public boolean enabled = false;

        @ConfigEntry(
                id = "foo",
                translation = "bazaarutils.config.test:entry.foo.value"
        )
        public String foo = "Hello";

        @ConfigEntry(
                id = "bar",
                translation = "bazaarutils.config.test:entry.bar.value"
        )
        @Comment(
                value = "This is a description comment of the objects' property",
                translation = "bazaarutils.config.test:entry.bar.description"
        )
        public String bar = "world!";

        @Override
        public Text getSummary(int index) {
            return Text.translatable("bazaarutils.config.test:entry.value", index);
        }

        @Override
        public Text getDescription(int index) {
            return Text.translatable("bazaarutils.config.test:entry.description", index);
        }
    }
}
