package com.github.mkram17.bazaarutils.utils.minecraft.components;

import com.github.mkram17.bazaarutils.utils.Util;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LoreParser {

    private LoreParser() {}

    public static List<Text> lines(ItemStack stack) {
        LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
        return lore != null ? lore.lines() : List.of();
    }

    public static String joined(ItemStack stack) {
        return lines(stack).stream()
                .map(t -> Util.stripFormatCodes(t.getString()))
                .collect(Collectors.joining(" "));
    }

    public static Optional<String> matchGroup(ItemStack stack, Pattern pattern, String group) {
        Matcher matcher = pattern.matcher(joined(stack));
        if (!matcher.find()) return Optional.empty();
        return Optional.ofNullable(matcher.group(group));
    }

    public static Optional<Double> matchDouble(ItemStack stack, Pattern pattern, String group, String errorContext) {
        return matchGroup(stack, pattern, group).flatMap(raw -> {
            try {
                return Optional.of(Double.parseDouble(raw.replace(",", "")));
            } catch (NumberFormatException e) {
                Util.notifyError("Failed to parse double from lore (" + errorContext + "): " + raw, e);
                return Optional.empty();
            }
        });
    }

    public static Optional<Integer> matchInt(ItemStack stack, Pattern pattern, String group, String errorContext) {
        return matchGroup(stack, pattern, group).flatMap(raw -> {
            try {
                return Optional.of(Integer.parseInt(raw.replace(",", "")));
            } catch (NumberFormatException e) {
                Util.notifyError("Failed to parse int from lore (" + errorContext + "): " + raw, e);
                return Optional.empty();
            }
        });
    }
}