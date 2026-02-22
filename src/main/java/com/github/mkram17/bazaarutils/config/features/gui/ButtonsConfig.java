package com.github.mkram17.bazaarutils.config.features.gui;

import com.teamresourceful.resourcefulconfig.api.annotations.*;
import lombok.Getter;

@Category(
        value = "buttons_config",
        categories = {
                ButtonsConfig.BookmarksConfig.class
        }
)
@ConfigInfo(
        title = "Buttons Config",
        titleTranslation = "bazaarutils.config.buttons.category.value",
        description = "Configurations for the buttons to be injected/handled by the mod",
        descriptionTranslation = "bazaarutils.config.buttons.category.description",
        icon = "pointer"
)
public final class ButtonsConfig {
    @ConfigEntry(
            id = "open_settings",
            translation = "bazaarutils.config.buttons.open_settings.value"
    )
    @Comment(
            value = "Adds a button to selected menus/screen to quick access the mods' settings.",
            translation = "bazaarutils.config.buttons.open_settings.description"
    )
    public static final WidgetButton OPEN_SETTINGS_BUTTON = new WidgetButton(true);

    @ConfigEntry(
            id = "open_orders",
            translation = "bazaarutils.config.buttons.open_orders.value"
    )
    @Comment(
            value = """
            Adds a button to selected menus/screen to quick access your orders page.
            
            Requires a §dBooster Cookie§r effect active in order to function.
            """,
            translation = "bazaarutils.config.buttons.open_orders.description"
    )
    public static final WidgetButton OPEN_ORDERS_BUTTON = new WidgetButton(true);

    @ConfigEntry(id = "container_buttons_separator")
    @ConfigOption.Hidden
    @ConfigOption.Separator(
            value = "bazaarutils.config.buttons.separator.container_buttons.value",
            description = "bazaarutils.config.buttons.separator.container_buttons.description"
    )
    public static boolean CONTAINER_BUTTONS_SEPARATOR = true;

    @ConfigEntry(
            id = "cancel_order_and_search",
            translation = "bazaarutils.config.buttons.cancel_order_and_search.value"
    )
    @Comment(
            value = "Adds a button to an unfilled orders' (or offer) settings page to cancel it and search once again the item.",
            translation = "bazaarutils.config.buttons.cancel_order_and_search.description"
    )
    public static final SmallContainerButton CANCEL_ORDER_AND_SEARCH = new SmallContainerButton(false, 25);

    @Category(value = "bookmarks")
    @ConfigInfo(
            title = "Bookmarks",
            titleTranslation = "bazaarutils.config.buttons.bookmarks.category.value",
            icon = "bookmark"
    )
    public static final class BookmarksConfig {
        @ConfigEntry(id = "introductory_separator")
        @ConfigOption.Hidden
        @ConfigOption.Separator(
                value = "bazaarutils.config.buttons.bookmarks.separator.introductory.value",
                description = "bazaarutils.config.buttons.bookmarks.separator.introductory.description"
        )
        public static boolean BOOKMARKS_INTRODUCTORY_SEPARATOR = true;

        @ConfigEntry(
                id = "open_bookmark",
                translation = "bazaarutils.config.buttons.bookmarks.open_bookmark.value"
        )
        @Comment(
                value = "Configures the button that appears on selected menus/screen to quick search the relevant bookmark.",
                translation = "bazaarutils.config.buttons.bookmarks.open_bookmark.description"
        )
        public static final WidgetButton OPEN_BOOKMARK_BUTTON = new WidgetButton(true);

        @ConfigEntry(
                id = "toggle_bookmark",
                translation = "bazaarutils.config.buttons.bookmarks.toggle_bookmark.value"
        )
        @Comment(
                value = "Adds a button to every item's page to toggle a quick access button to search the same item on the Bazaar.",
                translation = "bazaarutils.config.buttons.bookmarks.toggle_bookmark.description"
        )
        public static final SmallContainerButton TOGGLE_BOOKMARK_BUTTON = new SmallContainerButton(true, 0);
    }

    @ConfigObject
    public static final class WidgetButton {
        @Getter
        @ConfigEntry(
                id = "enabled",
                translation = "bazaarutils.config.buttons.button:widget.enabled.value"
        )
        @Comment(
                value = "Whether the button will be registered or not",
                translation = "bazaarutils.config.buttons.button:widget.enabled.description"
        )
        public boolean enabled;

        @ConfigEntry(
                id = "size",
                translation = "bazaarutils.config.buttons.button:widget.size.value"
        )
        public int size = 18;

        @ConfigEntry(
                id = "spacing",
                translation = "bazaarutils.config.buttons.button:widget.spacing.value"
        )
        public int spacing = 4;

        public WidgetButton(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @ConfigObject
    public static final class SmallContainerButton {
        @Getter
        @ConfigEntry(
                id = "enabled",
                translation = "bazaarutils.config.buttons.button:container.enabled.value"
        )
        @Comment(
                value = "Whether the button will be registered or not",
                translation = "bazaarutils.config.buttons.button:container.enabled.description"
        )
        public boolean enabled;

        @Getter
        @ConfigEntry(
                id = "slot_number",
                translation = "bazaarutils.config.buttons.button:container.slot_number.value"
        )
        @Comment(
                value = "The container slot where the button will be registered at",
                translation = "bazaarutils.config.buttons.button:container.slot_number.description"
        )
        @ConfigOption.Range(min = 0, max = 35)
        public int slotNumber;

        public SmallContainerButton(boolean enabled, int slotNumber) {
            this.enabled = enabled;
            this.slotNumber = slotNumber;
        }
    }
}