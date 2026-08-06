package com.github.mkram17.bazaarutils.utils.minecraft.item.modifier;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.teamresourceful.resourcefulconfig.api.types.info.Translatable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public enum ModifyIndicator implements Translatable {
    PREFIX,
    SUFFIX,
    LORE,
    AT_MODIFICATION,
    DISABLED;

    @Override
    public String getTranslationKey() {
        return "bazaarutils.config.modify_indicator." + name().toLowerCase() + ".label";
    }

    public sealed interface IndicatorPlacement permits
            IndicatorPlacement.AtModification,
            IndicatorPlacement.NamePrefix,
            IndicatorPlacement.NameSuffix,
            IndicatorPlacement.LoreLine,
            IndicatorPlacement.Disabled {
        record AtModification(boolean prefix) implements IndicatorPlacement {
            public AtModification() {
                this(true);
            }
        }
        record NamePrefix() implements IndicatorPlacement {}
        record NameSuffix() implements IndicatorPlacement {}
        record LoreLine() implements IndicatorPlacement {}
        record Disabled() implements IndicatorPlacement {}

        AtModification AT_MODIFICATION = new AtModification();
        NamePrefix NAME_PREFIX = new NamePrefix();
        NameSuffix NAME_SUFFIX = new NameSuffix();
        LoreLine LORE_LINE = new LoreLine();
        Disabled DISABLED = new Disabled();
    }

    public static final Component INDICATOR =
            Component.literal("₿")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GOLD)
                            .withItalic(false)
                            .withBold(false));

    public static final Component INDICATOR_WITH_SPACE =
            Component.empty()
                    .append(INDICATOR)
                    .append(" ");

    public static final Component SPACE_WITH_INDICATOR =
            Component.empty()
                    .append(" ")
                    .append(INDICATOR);

    public static final Component INDICATOR_LABEL =
            Component.literal("Modified by BazaarUtils")
                    .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false));

    public static final Component INDICATOR_LABEL_LINE =
            Component.empty()
                    .append(INDICATOR_WITH_SPACE)
                    .append(INDICATOR_LABEL);

    public IndicatorPlacement resolve(AbstractItemModifier modifier) {
        return switch (this) {
            case PREFIX -> IndicatorPlacement.NAME_PREFIX;
            case SUFFIX -> IndicatorPlacement.NAME_SUFFIX;
            case LORE -> IndicatorPlacement.LORE_LINE;
            case AT_MODIFICATION -> modifier.indicatorPlacement();
            case DISABLED -> IndicatorPlacement.DISABLED;
        };
    }

    public static void applyPlacement(List<Component> lines, IndicatorPlacement placement) {
        if (lines.isEmpty()) return;

        switch (placement) {
            case IndicatorPlacement.NamePrefix ignored ->
                    lines.set(0, Component.empty().append(INDICATOR_WITH_SPACE).append(lines.getFirst()));
            case IndicatorPlacement.NameSuffix ignored ->
                    lines.set(0, Component.empty().append(lines.getFirst()).append(SPACE_WITH_INDICATOR));
            case IndicatorPlacement.LoreLine ignored -> {
                lines.add(Component.empty());
                lines.add(INDICATOR_LABEL_LINE);
            }
            case IndicatorPlacement.AtModification ignored -> {}
            case IndicatorPlacement.Disabled ignored -> {}
        }
    }

    public static void apply(List<Component> lines, ModifyIndicator indicator) {
        switch (indicator) {
            case PREFIX -> lines.set(0, Component.empty().append(ModifyIndicator.INDICATOR_WITH_SPACE).append(lines.getFirst()));
            case SUFFIX -> lines.set(0, Component.empty().append(lines.getFirst()).append(ModifyIndicator.SPACE_WITH_INDICATOR));
            case LORE -> {
                lines.add(Component.empty());
                lines.add(ModifyIndicator.INDICATOR_LABEL_LINE);
            }
            case AT_MODIFICATION, DISABLED -> {}
        }
    }
}