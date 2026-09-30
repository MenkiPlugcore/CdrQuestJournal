package com.menkiestes.cdrquestjournal.storage;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.QuestHistoryEntry;
import com.menkiestes.cdrquestjournal.model.QuestType;
import com.menkiestes.cdrquestjournal.model.TerminalOutcome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class QuestHistoryStore {
    private final CdrQuestJournalPlugin plugin;
    private final File file;
    private final Map<UUID, List<QuestHistoryEntry>> history = new LinkedHashMap<>();

    public QuestHistoryStore(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quest-history.yml");
        reload();
    }

    public synchronized void reload() {
        history.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;

        for (String uuidRaw : players.getKeys(false)) {
            UUID playerId;
            try { playerId = UUID.fromString(uuidRaw); }
            catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring invalid quest history UUID: " + uuidRaw);
                continue;
            }
            ConfigurationSection entries = players.getConfigurationSection(uuidRaw);
            if (entries == null) continue;
            List<QuestHistoryEntry> loaded = new ArrayList<>();
            for (String key : entries.getKeys(false)) {
                ConfigurationSection section = entries.getConfigurationSection(key);
                if (section == null) continue;
                try {
                    String questId = section.getString("quest-id", "");
                    QuestType type = QuestType.from(section.getString("quest-type", "STORY"));
                    long startedAt = section.getLong("started-at", 0L);
                    long endedAt = section.getLong("ended-at", startedAt);
                    TerminalOutcome outcome = TerminalOutcome.valueOf(section.getString("outcome", "FAILED").toUpperCase());
                    String giver = section.getString("giver", "Unknown");
                    String cycleKey = section.getString("cycle-key", "");
                    loaded.add(new QuestHistoryEntry(playerId, questId, type, startedAt, endedAt, outcome, giver, cycleKey));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Ignoring malformed quest history entry " + uuidRaw + "/" + key + ": " + ex.getMessage());
                }
            }
            loaded.sort(Comparator.comparingLong(QuestHistoryEntry::endedAt).reversed());
            if (!loaded.isEmpty()) history.put(playerId, loaded);
        }
    }

    public synchronized boolean record(QuestHistoryEntry entry) {
        List<QuestHistoryEntry> entries = history.computeIfAbsent(entry.playerId(), ignored -> new ArrayList<>());
        boolean duplicate = entries.stream().anyMatch(existing ->
                existing.questId().equalsIgnoreCase(entry.questId())
                        && existing.startedAt() == entry.startedAt()
                        && existing.outcome() == entry.outcome());
        if (duplicate) return false;
        entries.add(entry);
        entries.sort(Comparator.comparingLong(QuestHistoryEntry::endedAt).reversed());
        save();
        return true;
    }

    public synchronized List<QuestHistoryEntry> history(UUID playerId) {
        List<QuestHistoryEntry> entries = history.get(playerId);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    public synchronized Optional<QuestHistoryEntry> latest(UUID playerId, String questId) {
        if (questId == null) return Optional.empty();
        return history(playerId).stream()
                .filter(entry -> entry.questId().equalsIgnoreCase(questId))
                .max(Comparator.comparingLong(QuestHistoryEntry::endedAt));
    }

    public synchronized boolean hasCompleted(UUID playerId, String questId) {
        return history(playerId).stream().anyMatch(entry ->
                entry.questId().equalsIgnoreCase(questId) && entry.outcome() == TerminalOutcome.COMPLETED);
    }

    public synchronized int count(UUID playerId) {
        List<QuestHistoryEntry> entries = history.get(playerId);
        return entries == null ? 0 : entries.size();
    }

    private synchronized void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for quest history.");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<QuestHistoryEntry>> playerEntry : history.entrySet()) {
            int index = 0;
            for (QuestHistoryEntry entry : playerEntry.getValue()) {
                String base = "players." + playerEntry.getKey() + ".entry-" + (++index);
                yaml.set(base + ".quest-id", entry.questId());
                yaml.set(base + ".quest-type", entry.questType().name());
                yaml.set(base + ".started-at", entry.startedAt());
                yaml.set(base + ".ended-at", entry.endedAt());
                yaml.set(base + ".outcome", entry.outcome().name());
                yaml.set(base + ".giver", entry.giver());
                yaml.set(base + ".cycle-key", entry.cycleKey());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save quest-history.yml", ex);
        }
    }
}
