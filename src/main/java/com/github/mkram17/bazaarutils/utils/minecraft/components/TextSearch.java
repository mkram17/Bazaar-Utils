package com.github.mkram17.bazaarutils.utils.minecraft.components;

import net.minecraft.network.chat.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TextSearch {

    private TextSearch() {}

    public static int indexOf(List<Component> lines, String contains) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getString().contains(contains)) return i;
        }

        return -1;
    }

    public static int lastIndexOf(List<Component> lines, String contains) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).getString().contains(contains)) return i;
        }

        return -1;
    }

    public static Optional<Component> findLine(List<Component> lines, String contains) {
        return lines.stream()
                .filter(text -> text.getString().contains(contains))
                .findFirst();
    }

    public static List<Component> findSpanning(List<Component> lines, String match) {
        String combined = lines.stream()
                .map(Component::getString)
                .collect(Collectors.joining(" "));

        int matchStart = combined.indexOf(match);
        if (matchStart == -1) return List.of();

        int matchEnd = matchStart + match.length();
        List<Component> result = new LinkedList<>();
        int offset = 0;

        for (Component line : lines) {
            int end = offset + line.getString().length();
            if (offset < matchEnd && end > matchStart) result.add(line);
            offset = end;
        }

        return result.isEmpty() ? List.of() : result;
    }
}