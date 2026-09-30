package com.menkiestes.cdrquestjournal.storage;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.LimitedQuestWindow;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class LimitedScheduleStore {
    private final CdrQuestJournalPlugin plugin;
    private final File file;
    private final Map<String, LimitedQuestWindow> windows = new LinkedHashMap<>();

    public LimitedScheduleStore(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "limited.yml");
        reload();
    }

    public synchronized void reload() {
        windows.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("limited");
        if (root == null) return;
        for (String questId : root.getKeys(false)) {
            long start = root.getLong(questId + ".start-at", 0L);
            long end = root.getLong(questId + ".end-at", 0L);
            if (start <= 0L || end <= start) {
                plugin.getLogger().warning("Ignoring invalid limited schedule for " + questId);
                continue;
            }
            windows.put(questId.toLowerCase(), new LimitedQuestWindow(start, end));
        }
    }

    public synchronized Optional<LimitedQuestWindow> get(String questId) {
        if (questId == null) return Optional.empty();
        return Optional.ofNullable(windows.get(questId.toLowerCase()));
    }

    public synchronized void set(String questId, long startAt, long endAt) {
        windows.put(questId.toLowerCase(), new LimitedQuestWindow(startAt, endAt));
        save();
    }

    public synchronized boolean updateEnd(String questId, long endAt) {
        LimitedQuestWindow current = windows.get(questId.toLowerCase());
        if (current == null || endAt <= current.startAt()) return false;
        windows.put(questId.toLowerCase(), new LimitedQuestWindow(current.startAt(), endAt));
        save();
        return true;
    }

    public synchronized boolean clear(String questId) {
        boolean removed = windows.remove(questId.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, LimitedQuestWindow> entry : windows.entrySet()) {
            String base = "limited." + entry.getKey();
            yaml.set(base + ".start-at", entry.getValue().startAt());
            yaml.set(base + ".end-at", entry.getValue().endAt());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save limited.yml", ex);
        }
    }
}
