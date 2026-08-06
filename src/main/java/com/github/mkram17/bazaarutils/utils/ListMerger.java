package com.github.mkram17.bazaarutils.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ListMerger<T> {
    private final List<T> source;
    private final List<T> output = new ArrayList<>();
    private int cursor = 0;

    public ListMerger(List<T> source) {
        this.source = List.copyOf(source);
    }

    public List<T> source() {
        return source;
    }

    public int cursor() {
        return cursor;
    }

    public boolean canRead() {
        return cursor < source.size();
    }

    public T read() {
        return source.get(cursor++);
    }

    public T peek() {
        return source.get(cursor);
    }

    public void add(T item) {
        output.add(item);
    }

    public void copy() {
        output.add(source.get(cursor++));
    }

    public void skip() {
        cursor++;
    }

    public void copyTo(int indexInclusive) {
        while (cursor <= indexInclusive && canRead()) copy();
    }

    public void addUntil(Predicate<T> predicate) {
        while (canRead() && !predicate.test(peek())) copy();
    }

    public void addUntilAfter(Predicate<T> predicate) {
        addUntil(predicate);
        if (canRead()) copy();
    }

    public void addRemaining() {
        while (canRead()) copy();
    }

    public List<T> destination() { return output; }
}