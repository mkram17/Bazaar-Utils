package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.function.Consumer;

public final class ScreenHistory {
    private static final int CAPACITY = 8;

    private final ScreenManager.ScreenSnapshot[] slots = new ScreenManager.ScreenSnapshot[CAPACITY];
    private int head = 0;
    private int size = 0;

    void push(ScreenManager.ScreenSnapshot snapshot) {
        head = (head - 1 + CAPACITY) % CAPACITY;
        slots[head] = snapshot;
        if (size < CAPACITY) size++;
    }

    ScreenManager.ScreenSnapshot peek() {
        return size == 0 ? null : slots[head];
    }

    ScreenManager.ScreenSnapshot get(int depth) {
        if (depth < 0 || depth >= size) return null;
        return slots[(head + depth) % CAPACITY];
    }

    void set(int depth, ScreenManager.ScreenSnapshot snapshot) {
        if (depth < 0 || depth >= size) return;
        slots[(head + depth) % CAPACITY] = snapshot;
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
            ScreenManager.ScreenSnapshot snap = get(i);
            joiner.add(snap != null && snap.type() != null ? snap.type().name() : "???");
        }

        return joiner.toString();
    }

    /** For findBack iteration — skips depth 0 (current). */
    void forEachFrom(Consumer<ScreenManager.ScreenSnapshot> action) {
        for (int i = 1; i < size; i++) {
            action.accept(get(i));
        }
    }
}