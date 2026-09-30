package com.menkiestes.cdrquestjournal.storage;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.TurnInState;
import com.menkiestes.cdrquestjournal.model.TurnInTransaction;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class TurnInTransactionStore {
    private final CdrQuestJournalPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, TurnInTransaction>> transactions = new LinkedHashMap<>();

    public TurnInTransactionStore(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-turnins.yml");
        reload();
    }

    public synchronized void reload() {
        transactions.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("transactions");
        if (root == null) return;

        for (String uuidRaw : root.getKeys(false)) {
            UUID playerId;
            try { playerId = UUID.fromString(uuidRaw); }
            catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring invalid player UUID in pending-turnins.yml: " + uuidRaw);
                continue;
            }
            ConfigurationSection playerSection = root.getConfigurationSection(uuidRaw);
            if (playerSection == null) continue;
            Map<String, TurnInTransaction> playerTransactions = new LinkedHashMap<>();
            for (String questKey : playerSection.getKeys(false)) {
                ConfigurationSection tx = playerSection.getConfigurationSection(questKey);
                if (tx == null) continue;
                try {
                    UUID transactionId = UUID.fromString(tx.getString("transaction-id", UUID.randomUUID().toString()));
                    TurnInState state = TurnInState.valueOf(tx.getString("state", "PREPARED").toUpperCase());
                    String questId = tx.getString("quest-id", questKey);
                    TurnInTransaction transaction = new TurnInTransaction(
                            transactionId,
                            playerId,
                            questId,
                            tx.getString("cycle-key", ""),
                            tx.getLong("prepared-at", 0L),
                            tx.getLong("rewarded-at", 0L),
                            state,
                            tx.getInt("npc-id", -1),
                            tx.getString("npc-name", "Quest Giver")
                    );
                    playerTransactions.put(questId.toLowerCase(), transaction);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Ignoring invalid turn-in transaction for " + uuidRaw + "/" + questKey + ": " + ex.getMessage());
                }
            }
            if (!playerTransactions.isEmpty()) transactions.put(playerId, playerTransactions);
        }
    }

    public synchronized Optional<TurnInTransaction> get(UUID playerId, String questId) {
        Map<String, TurnInTransaction> player = transactions.get(playerId);
        if (player == null || questId == null) return Optional.empty();
        return Optional.ofNullable(player.get(questId.toLowerCase()));
    }

    public synchronized List<TurnInTransaction> forPlayer(UUID playerId) {
        Map<String, TurnInTransaction> player = transactions.get(playerId);
        return player == null ? List.of() : new ArrayList<>(player.values());
    }

    public synchronized void put(TurnInTransaction transaction) {
        transactions.computeIfAbsent(transaction.playerId(), ignored -> new LinkedHashMap<>())
                .put(transaction.questId().toLowerCase(), transaction);
        save();
    }

    public synchronized boolean remove(UUID playerId, String questId) {
        Map<String, TurnInTransaction> player = transactions.get(playerId);
        if (player == null) return false;
        boolean removed = player.remove(questId.toLowerCase()) != null;
        if (player.isEmpty()) transactions.remove(playerId);
        if (removed) save();
        return removed;
    }

    public synchronized void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for turn-in transactions.");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, TurnInTransaction>> playerEntry : transactions.entrySet()) {
            for (TurnInTransaction tx : playerEntry.getValue().values()) {
                String base = "transactions." + playerEntry.getKey() + "." + tx.questId().toLowerCase();
                yaml.set(base + ".transaction-id", tx.transactionId().toString());
                yaml.set(base + ".quest-id", tx.questId());
                yaml.set(base + ".cycle-key", tx.cycleKey());
                yaml.set(base + ".prepared-at", tx.preparedAt());
                yaml.set(base + ".rewarded-at", tx.rewardedAt());
                yaml.set(base + ".state", tx.state().name());
                yaml.set(base + ".npc-id", tx.npcId());
                yaml.set(base + ".npc-name", tx.npcName());
            }
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Could not save pending-turnins.yml", ex); }
    }
}
