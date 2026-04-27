package com.github.mkram17.bazaarutils.utils.minecraft;

import java.util.List;

public final class SequenceChatFilter {
    private final List<String> matchers;
    private int cursor = 0;

    public SequenceChatFilter(String... matchers) {
        this.matchers = List.of(matchers);
    }

    public boolean shouldRemove(String message) {
        if (matchers.isEmpty()) return false;

        int index = indexOf(message);

        if (index == -1) {
            cursor = 0;
            return false;
        }

        if (index == cursor) {
            cursor = (cursor + 1) % matchers.size();
            return true;
        }

        // Out-of-order hit: reset, but restart sequence if it's index 0
        cursor = 0;
        if (index == 0) {
            cursor = 1;
            return true;
        }

        return false;
    }

    public void reset() {
        cursor = 0;
    }

    private int indexOf(String message) {
        for (int i = 0; i < matchers.size(); i++) {
            if (message.contains(matchers.get(i))) return i;
        }

        return -1;
    }
}