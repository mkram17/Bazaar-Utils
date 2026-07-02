package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.function.Consumer;

public final class ScreenHistory {
    private static final int CAPACITY = 8;

    private final ScreenContext[] slots = new ScreenContext[CAPACITY];
    private int head = 0;
    private int size = 0;

    void push(ScreenContext context) {
        head = (head - 1 + CAPACITY) % CAPACITY;
        slots[head] = context;
        if (size < CAPACITY) size++;
    }

    ScreenContext peek() {
        return size == 0 ? null : slots[head];
    }

    ScreenContext get(int depth) {
        if (depth < 0 || depth >= size) return null;
        return slots[(head + depth) % CAPACITY];
    }

    void set(int depth, ScreenContext context) {
        if (depth < 0 || depth >= size) return;
        slots[(head + depth) % CAPACITY] = context;
    }

    void clear() {
        Arrays.fill(slots, null);
        head = 0;
        size = 0;
    }

    int size() {
        return size;
    }

    boolean isEmpty() {
        return size == 0;
    }

    String toBreadcrumb() {
        StringJoiner joiner = new StringJoiner(" › ");

        for (int i = 0; i < size; i++) {
            ScreenContext ctx = get(i);
            joiner.add(ctx != null ? ctx.type().map(ScreenType::name).orElse("???") : "???");
        }

        return joiner.toString();
    }

    /** For findBack iteration — skips depth 0 (current). */
    void forEachFrom(Consumer<ScreenContext> action) {
        for (int i = 1; i < size; i++) {
            action.accept(get(i));
        }
    }
}
