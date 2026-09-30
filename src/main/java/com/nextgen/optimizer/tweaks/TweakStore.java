package com.nextgen.optimizer.tweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the original values captured before each tweak was applied, so any
 * tweak can be reverted exactly — even after the app or PC restarts. The file
 * is written atomically; a tweak is only applied after its originals are saved.
 */
public class TweakStore {

    public static class Entry {
        public long appliedAt;
        public List<Map<String, String>> originals = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public TweakStore(Path file) {
        this.file = file;
        load();
    }

    public static TweakStore defaultStore() {
        return new TweakStore(Path.of(System.getProperty("user.home"), ".nextgen", "tweaks", "state.json"));
    }

    public synchronized Entry get(String tweakId) {
        return entries.get(tweakId);
    }

    public synchronized boolean has(String tweakId) {
        return entries.containsKey(tweakId);
    }

    public synchronized List<String> ids() {
        return new ArrayList<>(entries.keySet());
    }

    public synchronized boolean put(String tweakId, Entry entry) {
        Entry previous = entries.put(tweakId, entry);
        if (save()) return true;
        if (previous == null) entries.remove(tweakId); else entries.put(tweakId, previous);
        return false;
    }

    public synchronized boolean remove(String tweakId) {
        Entry previous = entries.remove(tweakId);
        if (previous == null || save()) return true;
        entries.put(tweakId, previous);
        return false;
    }

    private void load() {
        try {
            if (!Files.exists(file)) return;
            Map<String, Entry> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<LinkedHashMap<String, Entry>>() {}.getType());
            if (loaded != null) entries.putAll(loaded);
        } catch (Exception e) {
            // Keep a copy of an unreadable file instead of silently discarding originals.
            try {
                Files.copy(file, file.resolveSibling("state.corrupt-" + System.currentTimeMillis() + ".json"));
            } catch (IOException ignored) {}
        }
    }

    protected boolean save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(entries), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) {
            System.err.println("[TweakStore] Failed to save: " + e.getMessage());
            return false;
        }
    }
}
