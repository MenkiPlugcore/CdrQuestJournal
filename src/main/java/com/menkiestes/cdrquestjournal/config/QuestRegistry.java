package com.menkiestes.cdrquestjournal.config;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.ObjectiveDefinition;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class QuestRegistry {
    private final CdrQuestJournalPlugin plugin;
    private volatile Map<String, QuestDefinition> definitions = Map.of();

    public QuestRegistry(CdrQuestJournalPlugin plugin) { this.plugin = plugin; }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection questsSection = yaml.getConfigurationSection("quests");
        if (questsSection == null) {
            definitions = Map.of();
            plugin.getLogger().warning("quests.yml tidak memiliki section 'quests'.");
            return;
        }

        Map<String, QuestDefinition> loaded = new LinkedHashMap<>();
        for (String questId : questsSection.getKeys(false)) {
            ConfigurationSection quest = questsSection.getConfigurationSection(questId);
            if (quest == null) continue;
            Map<String, ObjectiveDefinition> objectives = new LinkedHashMap<>();
            ConfigurationSection objectivesSection = quest.getConfigurationSection("objectives");
            if (objectivesSection != null) {
                for (String objectiveId : objectivesSection.getKeys(false)) {
                    ConfigurationSection objective = objectivesSection.getConfigurationSection(objectiveId);
                    if (objective == null) continue;
                    objectives.put(objectiveId, new ObjectiveDefinition(
                            objectiveId,
                            objective.getString("text", objectiveId),
                            objective.getInt("target", 1)
                    ));
                }
            }

            QuestType type = QuestType.from(quest.getString("type", "STORY"));
            loaded.put(questId.toLowerCase(), new QuestDefinition(
                    questId,
                    quest.getString("title", questId),
                    type,
                    quest.getString("giver", "Unknown"),
                    quest.getLong("time-limit-seconds", 0L),
                    quest.getStringList("description"),
                    objectives,
                    quest.getStringList("rewards"),
                    quest.getStringList("failure"),
                    quest.getStringList("expiration")
            ));
        }
        definitions = Map.copyOf(loaded);
        plugin.getLogger().info("Loaded " + definitions.size() + " quest journal definitions.");
    }

    public Optional<QuestDefinition> get(String questId) {
        if (questId == null) return Optional.empty();
        return Optional.ofNullable(definitions.get(questId.toLowerCase()));
    }

    public List<QuestDefinition> all() { return List.copyOf(definitions.values()); }
}
