package com.menkiestes.cdrquestjournal.storage;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.model.QuestStatus;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class SessionStore {
    private final CdrQuestJournalPlugin plugin;
    private final File file;

    public SessionStore(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sessions.yml");
    }

    public Map<UUID, Map<String, QuestSession>> loadAll() {
        Map<UUID, Map<String, QuestSession>> result = new LinkedHashMap<>();
        if (!file.exists()) return result;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("sessions");
        if (root == null) return result;

        for (String uuidRaw : root.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidRaw); }
            catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid UUID in sessions.yml: " + uuidRaw);
                continue;
            }
            ConfigurationSection playerSection = root.getConfigurationSection(uuidRaw);
            if (playerSection == null) continue;
            Map<String, QuestSession> playerSessions = new LinkedHashMap<>();
            for (String questId : playerSection.getKeys(false)) {
                ConfigurationSection quest = playerSection.getConfigurationSection(questId);
                if (quest == null) continue;
                QuestStatus status;
                try { status = QuestStatus.valueOf(quest.getString("status", "ACTIVE").toUpperCase()); }
                catch (IllegalArgumentException ignored) { status = QuestStatus.ACTIVE; }
                Map<String, Integer> progress = new LinkedHashMap<>();
                ConfigurationSection progressSection = quest.getConfigurationSection("progress");
                if (progressSection != null) {
                    for (String objectiveId : progressSection.getKeys(false)) {
                        progress.put(objectiveId, Math.max(0, progressSection.getInt(objectiveId, 0)));
                    }
                }
                playerSessions.put(questId.toLowerCase(), new QuestSession(uuid, questId, quest.getLong("accepted-at", 0L), quest.getLong("expires-at", 0L), status, progress));
            }
            if (!playerSessions.isEmpty()) result.put(uuid, playerSessions);
        }
        return result;
    }

    public void saveAll(Map<UUID, Map<String, QuestSession>> sessions) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, QuestSession>> playerEntry : sessions.entrySet()) {
            String base = "sessions." + playerEntry.getKey();
            for (QuestSession session : playerEntry.getValue().values()) {
                String path = base + "." + session.questId();
                yaml.set(path + ".accepted-at", session.acceptedAt());
                yaml.set(path + ".expires-at", session.expiresAt());
                yaml.set(path + ".status", session.status().name());
                for (Map.Entry<String, Integer> progress : session.progress().entrySet()) {
                    yaml.set(path + ".progress." + progress.getKey(), progress.getValue());
                }
            }
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Could not save sessions.yml", ex); }
    }
}
