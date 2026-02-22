package com.github.mkram17.bazaarutils.features.chat;

import com.github.mkram17.bazaarutils.config.features.chat.ChatConfig;
import com.github.mkram17.bazaarutils.config.util.ConfigUtil;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.util.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import lombok.Getter;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.ArrayList;
import java.util.Map;

@Module
public class StashMessagesRemover extends BUListener implements BUToggleableFeature {
    private static final Map<String, Integer> REMOVE_MAP = Map.of(
            "materials stashed away", 0,
            "types of materials stashed", 1,
            "to pick them up", 2
    );

    private static final int SEQUENCE_LENGTH = REMOVE_MAP.size();

    public boolean isEnabled() {
        return ChatConfig.STASH_MESSAGES_REMOVER_TOGGLE;
    }

//    We need to consider whether we store this to a DataStorage interface or just keep it to a per-boot level
    public boolean stashPreviouslyClaimed = false;

    private transient ArrayList<String> pastMessages = new ArrayList<>();

    public StashMessagesRemover() {}

    private void registerStashClaimDetector() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message.getString().contains("You picked up")
                    && message.getString().contains("from your material stash")
                    && !stashPreviouslyClaimed) {
                stashPreviouslyClaimed = true;
                Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyAll(
                        "TIP - To claim stash more easily and quickly, use the Stash Helper keybind, " +
                                "which closes the bazaar and claims your stash! To disable stash messages, " +
                                "enable the \"Disable Stash Messages\" option in the Bazaar Utils config."));
            }
        });
    }

    private void registerStashRemover() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!isEnabled() || message.getString().contains("Mana")) {
                return true;
            }

            String currentMessageString = message.getString();
            int currentMessageRoleIndex = indexOfMessage(currentMessageString);
            boolean removeCurrentMessage;

            if (currentMessageRoleIndex == -1) {
                pastMessages.clear();
                removeCurrentMessage = false;
            } else if (currentMessageRoleIndex == pastMessages.size()) {
                pastMessages.add(currentMessageString);
                removeCurrentMessage = true;

                if (pastMessages.size() == SEQUENCE_LENGTH) {
                    pastMessages.clear();
                }
            } else {
                pastMessages.clear();
                removeCurrentMessage = false;

                if (currentMessageRoleIndex == 0) {
                    pastMessages.add(currentMessageString);
                    removeCurrentMessage = true;
                }
            }

            return !removeCurrentMessage;
        });
    }

    private static int indexOfMessage(String messageContent) {
        for (Map.Entry<String, Integer> entry : REMOVE_MAP.entrySet()) {
            if (messageContent.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return -1;
    }

    @Override
    protected void registerFabricEvents() {
        super.subscribeToMeteorEventBus = false;
        registerStashRemover();
        registerStashClaimDetector();
    }
}