package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.NpcBinding;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ReliabilityService {
    public record SanitizeResult(int duplicatesRemoved, int orphanJournalsRemoved) {
        public int totalRemoved() { return duplicatesRemoved + orphanJournalsRemoved; }
    }

    private final CdrQuestJournalPlugin plugin;
    private final JournalService journals;
    private final NpcBindingService bindings;

    public ReliabilityService(CdrQuestJournalPlugin plugin, JournalService journals, NpcBindingService bindings) {
        this.plugin = plugin;
        this.journals = journals;
        this.bindings = bindings;
    }

    public void enforceCrossplayFallback() {
        if (!plugin.getConfig().getBoolean("reliability.force-crossplay-safe-with-geyser", true)) return;
        boolean geyser = plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") != null
                || plugin.getServer().getPluginManager().getPlugin("Geyser") != null;
        if (!geyser || plugin.getConfig().getBoolean("journal-ui.crossplay-safe", true)) return;
        plugin.getConfig().set("journal-ui.crossplay-safe", true);
        plugin.saveConfig();
        plugin.getLogger().warning("Geyser terdeteksi: journal-ui.crossplay-safe dipaksa true untuk kompatibilitas Bedrock.");
    }

    public SanitizeResult sanitizePlayer(Player player) {
        boolean removeDuplicates = plugin.getConfig().getBoolean("reliability.cleanup-duplicate-journals", true);
        boolean removeOrphans = plugin.getConfig().getBoolean("reliability.cleanup-orphan-journals", true);
        if (!removeDuplicates && !removeOrphans) return new SanitizeResult(0, 0);

        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        int orphans = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!journals.isJournalOwnedBy(item, player.getUniqueId())) continue;
            String questId = journals.journalQuestId(item);
            String normalized = questId == null ? "" : questId.toLowerCase(Locale.ROOT);
            boolean active = !normalized.isBlank()
                    && journals.hasSession(player.getUniqueId(), normalized)
                    && journals.definition(normalized).isPresent();

            if (!active && removeOrphans) {
                player.getInventory().setItem(slot, null);
                orphans++;
                continue;
            }
            if (active && removeDuplicates && !seen.add(normalized)) {
                player.getInventory().setItem(slot, null);
                duplicates++;
            } else if (active) {
                seen.add(normalized);
            }
        }

        if ((duplicates + orphans) > 0) {
            debug("Sanitized " + player.getName() + ": duplicates=" + duplicates + ", orphanJournals=" + orphans);
        }
        return new SanitizeResult(duplicates, orphans);
    }

    public void runStartupDiagnostics() {
        List<QuestDefinition> definitions = plugin.getQuestRegistry().all();
        long story = definitions.stream().filter(q -> q.type() == QuestType.STORY).count();
        long daily = definitions.stream().filter(q -> q.type() == QuestType.DAILY).count();
        long limited = definitions.stream().filter(q -> q.type() == QuestType.LIMITED).count();

        int bound = 0;
        int unresolved = 0;
        int unbound = 0;
        boolean strictNpc = plugin.getConfig().getBoolean("npc.require-binding-for-start", true)
                || plugin.getConfig().getBoolean("npc.require-binding-for-turnin", true);
        for (QuestDefinition definition : definitions) {
            NpcBinding binding = bindings.binding(definition.id()).orElse(null);
            if (binding == null) {
                unbound++;
                continue;
            }
            bound++;
            if (bindings.resolveNpc(binding).isEmpty()) unresolved++;
        }

        int sessionCount = countYamlLeafSections(new File(plugin.getDataFolder(), "sessions.yml"), "sessions", true);
        int orphanSessions = countOrphanSessions();
        int pendingTurnIns = countYamlLeafSections(new File(plugin.getDataFolder(), "pending-turnins.yml"), "transactions", true);
        List<String> cycles = findStoryCycles();

        plugin.getLogger().info("========== CdrQuestJournal Diagnostics ==========");
        plugin.getLogger().info("Quests: " + definitions.size() + " total | STORY=" + story + " DAILY=" + daily + " LIMITED=" + limited);
        plugin.getLogger().info("NPC bindings: " + bound + " bound | " + unresolved + " unresolved | " + unbound + " unbound");
        plugin.getLogger().info("Sessions: " + sessionCount + " persisted | " + orphanSessions + " orphaned (kept for recovery)");
        plugin.getLogger().info("Pending turn-ins: " + pendingTurnIns);
        plugin.getLogger().info("Story chain cycles: " + cycles.size());
        plugin.getLogger().info("Crossplay-safe UI: " + plugin.getConfig().getBoolean("journal-ui.crossplay-safe", true));
        plugin.getLogger().info("Config schema: " + plugin.getConfig().getInt("config-version", 0) + "/" + ConfigMigrationService.CURRENT_SCHEMA);
        plugin.getLogger().info("=================================================");

        if (strictNpc && unbound > 0) {
            plugin.getLogger().warning(unbound + " quest belum punya NPC binding sementara strict NPC mode aktif.");
        }
        if (unresolved > 0) plugin.getLogger().warning(unresolved + " NPC binding tidak dapat di-resolve oleh Citizens.");
        if (orphanSessions > 0) plugin.getLogger().warning(orphanSessions + " orphan session dipertahankan agar progress tidak hilang jika quest definition dikembalikan.");
        for (String cycle : cycles) plugin.getLogger().severe("Circular STORY dependency: " + cycle);

        if (plugin.getConfig().getBoolean("reliability.log-details", false)) {
            for (QuestDefinition definition : definitions) {
                NpcBinding binding = bindings.binding(definition.id()).orElse(null);
                if (binding == null && strictNpc) debug("UNBOUND quest: " + definition.id());
                else if (binding != null && bindings.resolveNpc(binding).isEmpty()) debug("UNRESOLVED NPC: " + definition.id() + " -> " + binding.npcName() + " (#" + binding.npcId() + ")");
            }
        }
    }

    private int countOrphanSessions() {
        File file = new File(plugin.getDataFolder(), "sessions.yml");
        if (!file.exists()) return 0;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("sessions");
        if (root == null) return 0;
        int count = 0;
        for (String playerId : root.getKeys(false)) {
            ConfigurationSection player = root.getConfigurationSection(playerId);
            if (player == null) continue;
            for (String questId : player.getKeys(false)) {
                if (plugin.getQuestRegistry().get(questId).isEmpty()) count++;
            }
        }
        return count;
    }

    private int countYamlLeafSections(File file, String rootPath, boolean secondLevel) {
        if (!file.exists()) return 0;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection(rootPath);
        if (root == null) return 0;
        if (!secondLevel) return root.getKeys(false).size();
        int count = 0;
        for (String first : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(first);
            if (section != null) count += section.getKeys(false).size();
        }
        return count;
    }

    private List<String> findStoryCycles() {
        Map<String, QuestDefinition> stories = new HashMap<>();
        for (QuestDefinition definition : plugin.getQuestRegistry().all()) {
            if (definition.type() == QuestType.STORY) stories.put(definition.id().toLowerCase(Locale.ROOT), definition);
        }
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> cycles = new LinkedHashSet<>();
        for (String id : stories.keySet()) dfs(id, stories, visited, visiting, stack, cycles);
        return new ArrayList<>(cycles);
    }

    private void dfs(String id, Map<String, QuestDefinition> stories, Set<String> visited,
                     Set<String> visiting, Deque<String> stack, Set<String> cycles) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) return;
        stack.addLast(id);
        QuestDefinition definition = stories.get(id);
        if (definition != null) {
            List<String> dependencies = new ArrayList<>(definition.requiresAll());
            dependencies.addAll(definition.requiresAny());
            for (String depRaw : dependencies) {
                String dep = depRaw.toLowerCase(Locale.ROOT);
                if (!stories.containsKey(dep)) continue;
                if (visiting.contains(dep)) {
                    List<String> path = new ArrayList<>(stack);
                    int start = path.indexOf(dep);
                    if (start >= 0) {
                        List<String> cycle = new ArrayList<>(path.subList(start, path.size()));
                        cycle.add(dep);
                        cycles.add(String.join(" -> ", cycle));
                    }
                    continue;
                }
                dfs(dep, stories, visited, visiting, stack, cycles);
            }
        }
        stack.removeLast();
        visiting.remove(id);
        visited.add(id);
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("reliability.debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}
