package com.github.mkram17.bazaarutils.config.util.client.components.options.types;

import com.teamresourceful.resourcefulconfig.api.types.entries.ResourcefulConfigObjectEntry;
import com.teamresourceful.resourcefulconfig.client.UIConstants;
import com.teamresourceful.resourcefulconfig.client.components.options.Options;
import com.teamresourceful.resourcefulconfig.client.components.options.OptionsListWidget;
import com.teamresourceful.resourcefulconfig.client.components.options.types.ObjectOptionWidget;
import com.teamresourceful.resourcefulconfig.client.screens.base.ModalOverlay;
import net.minecraft.client.gui.Click;
import org.jetbrains.annotations.NotNull;

/**
 * Identical to RC's native {@link ObjectOptionWidget} except it fires
 * {@code onEditClose} when the edit overlay is dismissed — used to
 * trigger a config save after field edits.
 */
public class EntryEditOptionWidget extends ObjectOptionWidget {

    private final ResourcefulConfigObjectEntry entry;
    private final Runnable onEditClose;

    public EntryEditOptionWidget(ResourcefulConfigObjectEntry entry, Runnable onEditClose) {
        super(entry);
        this.entry       = entry;
        this.onEditClose = onEditClose;
    }

    @Override
    public void onClick(@NotNull Click event, boolean bl) {
        new EntryEditOverlay(entry, onEditClose).open();
    }

    // -------------------------------------------------------------------------

    private static class EntryEditOverlay extends ModalOverlay {

        private final ResourcefulConfigObjectEntry entry;
        private final Runnable onClose;

        protected EntryEditOverlay(ResourcefulConfigObjectEntry entry, Runnable onClose) {
            super();
            this.entry   = entry;
            this.onClose = onClose;
            this.title   = entry.getTitle(UIConstants.EDIT_OBJECT);
        }

        @Override
        protected void init() {
            super.init();
            OptionsListWidget list = addDrawableChild(
                    new OptionsListWidget(this.contentWidth, this.contentHeight)
            );
            list.setPosition(this.left, this.top);
            Options.populateOptions(list, this.entry.elements());
        }

        @Override
        public void close() {
            onClose.run();
            super.close();
        }
    }
}