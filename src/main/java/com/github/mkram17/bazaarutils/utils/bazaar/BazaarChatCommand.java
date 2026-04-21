package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public enum BazaarChatCommand implements Translatable, TooltipProvider {
    NONE (args -> {}),
    OPEN_BAZAAR (args -> PlayerActionUtil.runCommand("bazaar")),
    OPEN_ORDERS (args -> PlayerActionUtil.runCommand("managebazaarorders")),
    SEARCH_ITEM (args -> PlayerActionUtil.runCommand(("bz " + String.join(" ", args)).trim()));

    private final Consumer<String[]> action;

    BazaarChatCommand(Consumer<String[]> action) {
        this.action = action;
    }

    public void run(String... args) {
        action.accept(args);
    }

    @Override
    public String getTranslationKey() {
        return "bazaarutils.hypixel.commands.bazaar." + name().toLowerCase() + ".label";
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("bazaarutils.hypixel.commands.bazaar." + name().toLowerCase() + ".hint");
    }
}