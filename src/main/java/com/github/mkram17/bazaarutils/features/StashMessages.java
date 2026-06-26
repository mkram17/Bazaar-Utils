package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.config.BUConfigGui;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.features.keybinds.StashHelper;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;

public class StashMessages implements BUListener {
    public boolean shouldRemoveMessages(){
        return removeMessages;
    }
    @Setter
    private boolean removeMessages;
    @Setter @Getter
    private boolean stashPreviouslyClaimed = false;
    private transient ArrayList<String> pastMessages = new ArrayList<>(Collections.singleton(""));
    private static final String[] removeList = {" ", "materials stashed away", "type of material stashed", "to pick them up", "   "};

    public StashMessages(boolean removeMessages){
        this.removeMessages = removeMessages;
    }
    public void subscribe(){
        registerStashRemover();
        registerStashClaimDetector();
    }
    private void registerStashClaimDetector(){
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if(message.getString().contains("You picked up") && message.getString().contains("from your material stash") && !stashPreviouslyClaimed) {
                stashPreviouslyClaimed = true;
                Util.scheduleConfigSave();
                Util.tickExecuteLater(2, () -> PlayerActionUtil.notifyAll("TIP - To claim stash more easily and quickly, use the Stash Helper keybind, which closes the bazaar and claims your stash! To disable stash messages, enable the \"Disable Stash Messages\" option in the Bazaar Utils config."));
            }
        });
    }
    private void registerStashRemover() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!BUConfig.get().stashMessages.shouldRemoveMessages() || message.getString().contains("Mana")) return true;

            String currentMessageString = message.getString();
            int currentMessageRoleIndex = indexOfMessage(currentMessageString);
            if (pastMessages == null) {
                pastMessages = new ArrayList<>();
            }

            boolean removeCurrentMessage;

            if (currentMessageRoleIndex == -1) {
                pastMessages.clear();
                removeCurrentMessage = false;
            } else {
                if (currentMessageRoleIndex == pastMessages.size()) {
                    pastMessages.add(currentMessageString);
                    removeCurrentMessage = true;

                    if (pastMessages.size() == removeList.length)
                        pastMessages.clear();
                } else {
                    pastMessages.clear();
                    removeCurrentMessage = false;

                    if (currentMessageRoleIndex == 0) {
                        pastMessages.add(currentMessageString);
                        removeCurrentMessage = true;
                    }
                }
            }
            return !removeCurrentMessage;
        });
    }
    private static int indexOfMessage(String messageContent) {
        if(messageContent.equals(" "))
            return 0;
        if(messageContent.equals("  "))
            return 4;
        if(messageContent.contains("types of materials stashed"))
            return 2;
        for (int i = removeList.length - 2; i >= 1; i--) {
            if (messageContent.contains(removeList[i])) {
                return i;
            }
        }
        return -1;
    }

    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Disable Stash Messages"))
                .description(OptionDescription.of(Component.literal("When this option is ON, messages reminding you to pick up your stash will no longer appear in chat.")))
                .binding(false,
                        this::shouldRemoveMessages,
                        this::setRemoveMessages)
                .controller(BUConfigGui::createBooleanController)
                .build();
    }
}
