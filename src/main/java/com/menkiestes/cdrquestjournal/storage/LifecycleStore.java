package com.menkiestes.cdrquestjournal.storage;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.TerminalOutcome;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;

public final class LifecycleStore {
    private final CdrQuestJournalPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public LifecycleStore(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lifecycle.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public synchronized boolean isLocked(UUID playerId, String questId, String cycleKey) {
        if (cycleKey == null || cycleKey.isBlank()) return false;
        String stored = yaml.getString(path(playerId, questId) + ".cycle", "");
        return cycleKey.equals(stored);
    }

    public synchronized void record(UUID playerId, String questId, String cycleKey, TerminalOutcome outcome) {
        if (cycleKey == null || cycleKey.isBlank()) return;
        String base = path(playerId, questId);
        yaml.set(base + ".cycle", cycleKey);
        yaml.set(base + ".outcome", outcome.name());
        yaml.set(base + ".at", Instant.now().getEpochSecond());
        save();
    }

    private String path(UUID playerId, String questId) {
        return "locks." + playerId + "." + questId.toLowerCase();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save lifecycle.yml", ex);
        }
    }
}
