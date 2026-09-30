package com.menkiestes.cdrquestjournal.storage;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StoryProgressionStore {
    private final CdrQuestJournalPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Long>> completed = new LinkedHashMap<>();

    public StoryProgressionStore(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "story-progress.yml");
        reload();
    }

    public synchronized void reload() {
        completed.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidRaw : players.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidRaw); }
            catch (IllegalArgumentException ignored) { continue; }
            ConfigurationSection section = players.getConfigurationSection(uuidRaw + ".completed");
            if (section == null) continue;
            Map<String, Long> quests = new LinkedHashMap<>();
            for (String questId : section.getKeys(false)) {
                quests.put(questId.toLowerCase(), section.getLong(questId));
            }
            if (!quests.isEmpty()) completed.put(uuid, quests);
        }
    }

    public synchronized boolean isCompleted(UUID playerId, String questId) {
        Map<String, Long> quests = completed.get(playerId);
        return quests != null && quests.containsKey(questId.toLowerCase());
    }

    public synchronized long completedAt(UUID playerId, String questId) {
        Map<String, Long> quests = completed.get(playerId);
        return quests == null ? 0L : quests.getOrDefault(questId.toLowerCase(), 0L);
    }

    public synchronized int completedCount(UUID playerId) {
        Map<String, Long> quests = completed.get(playerId);
        return quests == null ? 0 : quests.size();
    }

    public synchronized void complete(UUID playerId, String questId, long completedAt) {
        completed.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                .putIfAbsent(questId.toLowerCase(), completedAt);
        save();
    }

    public synchronized boolean reset(UUID playerId, String questId) {
        Map<String, Long> quests = completed.get(playerId);
        if (quests == null) return false;
        boolean changed = quests.remove(questId.toLowerCase()) != null;
        if (quests.isEmpty()) completed.remove(playerId);
        if (changed) save();
        return changed;
    }

    public synchronized boolean resetAll(UUID playerId) {
        boolean changed = completed.remove(playerId) != null;
        if (changed) save();
        return changed;
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var playerEntry : completed.entrySet()) {
            String base = "players." + playerEntry.getKey() + ".completed.";
            for (var questEntry : playerEntry.getValue().entrySet()) {
                yaml.set(base + questEntry.getKey(), questEntry.getValue());
            }
        }
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save story-progress.yml: " + ex.getMessage());
        }
    }
}
