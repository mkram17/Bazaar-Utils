package com.github.mkram17.bazaarutils.utils.storage;

import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.mojang.serialization.Codec;
import com.github.mkram17.bazaarutils.utils.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class FolderStorage<T> {
    private static final BazaarLogger LOG = BazaarLogger.of(FolderStorage.class);

    private final String folder;
    private final Codec<T> codec;
    private final Map<String, DataStorage<T>> storages = new LinkedHashMap<>();
    private final Path folderPath;

    public FolderStorage(String folder, Codec<T> codec) {
        this.folder = folder;
        this.codec = codec;
        this.folderPath = DataStorage.DEFAULT_PATH.resolve(folder);
        load();
    }

    public void add(T value) { set(String.valueOf(value.hashCode()), value); }

    public void set(String id, T value) {
        final T captured = value;
        storages.computeIfAbsent(id, k -> new DataStorage<>(
                () -> captured, folder + "/" + id, codec
        )).save();
    }

    @Nullable
    public T get(String id) {
        DataStorage<T> s = storages.get(id);
        return s != null ? s.get() : null;
    }

    public void remove(String id) {
        DataStorage<T> s = storages.remove(id);
        if (s != null) s.delete();
    }

    public Map<String, T> getAll() {
        Map<String, T> result = new LinkedHashMap<>();
        storages.forEach((k, v) -> result.put(k, v.get()));
        return Collections.unmodifiableMap(result);
    }

    public boolean contains(String id) { return storages.containsKey(id); }

    public void refresh() { storages.clear(); load(); }

    private void load() {
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            LOG.error("Failed to create folder storage directory — path={}", folderPath, e);
            return;
        }

        try (Stream<Path> files = Files.list(folderPath)) {
            files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                    .forEach(file -> {
                        String id = file.getFileName().toString().replace(".json", "");
                        try {
                            storages.put(id, new DataStorage<>(
                                    () -> { throw new IllegalStateException("No default for folder entry: " + id); },
                                    folder + "/" + id, codec
                            ));
                        } catch (Exception e) {
                            LOG.warn("Failed to load folder entry — skipping file={}", file, e);
                        }
                    });
        } catch (IOException e) {
            LOG.error("Failed to list folder storage directory — path={}", folderPath, e);
            return;
        }

        LOG.info("FolderStorage loaded — folder={} entries={}", folder, storages.size());
    }
}