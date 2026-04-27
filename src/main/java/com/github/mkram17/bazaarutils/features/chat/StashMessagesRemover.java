package com.github.mkram17.bazaarutils.features.chat;

import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.minecraft.SequenceChatFilter;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;

@Module
public class StashMessagesRemover extends BUListener implements ToggleableFeature {
    private static final SequenceChatFilter STASH_FILTER = new SequenceChatFilter(
            "materials stashed away",
            "types of materials stashed",
            "to pick them up"
    );

    @Override
    public boolean isEnabled() {
        return ChatConfig.STASH_MESSAGES_REMOVER_TOGGLE;
    }

    public StashMessagesRemover() {}

    // We need to consider whether we store this to a DataStorage interface or just keep it to a per-boot level
    public transient boolean stashPreviouslyClaimed = false;

    @Subscription
    @OnlyOnSkyBlock
    private void onChat(ChatReceivedEvent.Pre event) {
        String message = event.getText();

        if (message.contains("You picked up") && message.contains("from your material stash")) {
            if (!stashPreviouslyClaimed) {
                stashPreviouslyClaimed = true;

                Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyAll(
                        "TIP - To claim stash more easily, use the Stash Helper keybind. " +
                                "To disable stash messages, enable \"Disable Stash Messages\" in the Bazaar Utils config."));
            }
            return;
        }

        if (!isEnabled() || message.contains("Mana")) return;

        if (STASH_FILTER.shouldRemove(message)) {
            event.cancel();
        }
    }
}