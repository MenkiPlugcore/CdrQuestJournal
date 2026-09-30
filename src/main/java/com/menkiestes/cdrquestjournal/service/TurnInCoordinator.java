package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.NpcBinding;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.TurnInState;
import com.menkiestes.cdrquestjournal.model.TurnInTransaction;
import com.menkiestes.cdrquestjournal.storage.TurnInTransactionStore;
import com.menkiestes.cdrquestjournal.util.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class TurnInCoordinator {
    private final CdrQuestJournalPlugin plugin;
    private final JournalService journal;
    private final NpcBindingService bindings;
    private final TurnInTransactionStore store;
    private final TurnInAuditLog audit;
    private final MessageService messages;

    public TurnInCoordinator(CdrQuestJournalPlugin plugin, JournalService journal, NpcBindingService bindings,
                             TurnInTransactionStore store, TurnInAuditLog audit, MessageService messages) {
        this.plugin = plugin;
        this.journal = journal;
        this.bindings = bindings;
        this.store = store;
        this.audit = audit;
        this.messages = messages;
    }

    public boolean prepare(Player player, String questId) {
        NpcBindingService.Validation validation = bindings.validateTurnIn(player, questId);
        if (validation != NpcBindingService.Validation.OK) {
            bindings.notifyValidation(player, questId, validation);
            return false;
        }

        TurnInTransaction existing = store.get(player.getUniqueId(), questId).orElse(null);
        if (existing != null) {
            if (existing.state() == TurnInState.REWARDED) {
                recoverRewarded(player, existing);
                messages.send(player, "turnin-already-rewarded", Map.of("quest", questId));
                return false;
            }
            if (!isPreparedStale(existing)) {
                messages.send(player, "turnin-busy", Map.of("quest", questId));
                return false;
            }
            store.remove(player.getUniqueId(), questId);
            audit.log("PREPARED_TIMEOUT", existing, "stale transaction released before retry");
        }

        if (!journal.isReadyForTurnIn(player, questId)) return false;
        if (!journal.hasJournal(player, questId)) {
            journal.restore(player, questId);
            messages.send(player, "journal-restored-turnin", Map.of("quest", questId));
            return false;
        }

        int requiredSlots = requiredFreeSlots(questId);
        int freeSlots = freeStorageSlots(player);
        if (freeSlots < requiredSlots) {
            messages.send(player, "turnin-space", Map.of(
                    "quest", questId,
                    "required", Integer.toString(requiredSlots),
                    "free", Integer.toString(freeSlots)
            ));
            return false;
        }

        QuestDefinition definition = journal.definition(questId).orElse(null);
        if (definition == null) return false;
        NpcBinding binding = bindings.binding(questId).orElse(null);
        TurnInTransaction tx = new TurnInTransaction(
                UUID.randomUUID(),
                player.getUniqueId(),
                definition.id(),
                journal.sessionCycleKey(player.getUniqueId(), questId),
                Instant.now().getEpochSecond(),
                0L,
                TurnInState.PREPARED,
                binding == null ? -1 : binding.npcId(),
                binding == null ? definition.giver() : binding.npcName()
        );
        store.put(tx);
        audit.log("PREPARED", tx, "freeSlots=" + freeSlots + ";required=" + requiredSlots);
        messages.send(player, "turnin-prepared", Map.of("quest", definition.title()));
        return true;
    }

    public boolean finalizeReward(Player player, String questId) {
        TurnInTransaction tx = store.get(player.getUniqueId(), questId).orElse(null);
        if (tx == null) {
            messages.send(player, "turnin-no-pending", Map.of("quest", questId));
            return false;
        }

        if (tx.state() == TurnInState.PREPARED) {
            tx = tx.markRewarded(Instant.now().getEpochSecond());
            store.put(tx);
            audit.log("REWARDED", tx, "reward chain reported success");
        }

        boolean committed = journal.commitRewardedTurnIn(player, questId);
        if (!committed && !journal.hasSession(player.getUniqueId(), questId)) committed = true;
        if (committed) {
            store.remove(player.getUniqueId(), questId);
            audit.log("COMMITTED", tx, "journal/session closed");
            bindings.clearContext(player);
            return true;
        }

        audit.log("COMMIT_DEFERRED", tx, "rewarded state persisted for recovery");
        messages.send(player, "turnin-pending-recovery", Map.of("quest", questId));
        return false;
    }

    public boolean abort(Player player, String questId) {
        TurnInTransaction tx = store.get(player.getUniqueId(), questId).orElse(null);
        if (tx == null) return true;
        if (tx.state() == TurnInState.REWARDED) {
            recoverRewarded(player, tx);
            messages.send(player, "turnin-cannot-abort-rewarded", Map.of("quest", questId));
            return false;
        }
        store.remove(player.getUniqueId(), questId);
        audit.log("ABORTED", tx, "reward chain aborted before REWARDED marker");
        bindings.clearContext(player);
        messages.send(player, "turnin-aborted", Map.of("quest", questId));
        return true;
    }

    public void recoverPlayer(Player player) {
        for (TurnInTransaction tx : store.forPlayer(player.getUniqueId())) {
            if (tx.state() == TurnInState.REWARDED) {
                recoverRewarded(player, tx);
            } else if (isPreparedStale(tx)) {
                store.remove(player.getUniqueId(), tx.questId());
                audit.log("RECOVERED_ABORT", tx, "stale PREPARED transaction released on recovery");
                messages.send(player, "turnin-retry", Map.of("quest", tx.questId()));
            }
        }
    }

    public void tickPlayer(Player player) {
        recoverPlayer(player);
    }

    public void save() {
        store.save();
    }

    private boolean recoverRewarded(Player player, TurnInTransaction tx) {
        boolean committed = !journal.hasSession(player.getUniqueId(), tx.questId())
                || journal.commitRewardedTurnIn(player, tx.questId());
        if (!committed) return false;
        store.remove(player.getUniqueId(), tx.questId());
        audit.log("RECOVERED_COMMIT", tx, "rewarded transaction finalized during recovery");
        bindings.clearContext(player);
        messages.send(player, "turnin-recovered", Map.of("quest", tx.questId()));
        return true;
    }

    private boolean isPreparedStale(TurnInTransaction tx) {
        if (tx.state() != TurnInState.PREPARED) return false;
        long timeout = Math.max(10L, plugin.getConfig().getLong("turn-in.prepare-timeout-seconds", 120L));
        return Instant.now().getEpochSecond() - tx.preparedAt() >= timeout;
    }

    private int requiredFreeSlots(String questId) {
        int global = Math.max(0, plugin.getConfig().getInt("turn-in.minimum-free-slots", 1));
        return Math.max(0, plugin.getConfig().getInt("turn-in.required-free-slots-by-quest." + questId, global));
    }

    private int freeStorageSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) free++;
        }
        return free;
    }
}
