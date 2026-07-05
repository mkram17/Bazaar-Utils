package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.teamresourceful.resourcefulconfig.api.types.info.TooltipProvider;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Consumer;

public enum BazaarChatCommand implements Translatable, TooltipProvider {
    OPEN_BAZAAR(args -> PlayerLogger.runCommand("bazaar")),
    OPEN_ORDERS(args -> PlayerLogger.runCommand("managebazaarorders")),
    SEARCH_ITEM(args -> PlayerLogger.runCommand(("bz " + String.join(" ", args)).trim()));

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
    public MutableComponent getTooltip() {
        return Component.translatable("bazaarutils.hypixel.commands.bazaar." + name().toLowerCase() + ".hint");
    }

    /**
     * Returns the bare command string for this value (no leading slash).
     * Used to build {@link net.minecraft.network.chat.ClickEvent.RunCommand} payloads.
     */
    public String commandFor(String... args) {
        return switch (this) {
            case OPEN_BAZAAR -> "bazaar";
            case OPEN_ORDERS -> "managebazaarorders";
            case SEARCH_ITEM -> ("bz " + String.join(" ", args)).trim();
        };
    }

    public Component clickHint() {
        return Component.literal("[")
                .append(getTooltip())
                .append("]").withStyle(ChatFormatting.DARK_GRAY);
    }
}