package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.config.QuestRegistry;
import com.menkiestes.cdrquestjournal.model.AvailabilityResult;
import com.menkiestes.cdrquestjournal.model.ObjectiveDefinition;
import com.menkiestes.cdrquestjournal.model.ProgressMode;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.model.QuestStatus;
import com.menkiestes.cdrquestjournal.model.QuestType;
import com.menkiestes.cdrquestjournal.model.StartResult;
import com.menkiestes.cdrquestjournal.model.TerminalOutcome;
import com.menkiestes.cdrquestjournal.storage.SessionStore;
import com.menkiestes.cdrquestjournal.util.MessageService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class JournalService {
    private final CdrQuestJournalPlugin plugin;
    private final QuestRegistry registry;
    private final SessionStore store;
    private final MessageService messages;
    private final QuestAvailabilityService availability;
    private final NamespacedKey journalKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey questKey;
    private final Map<UUID, Map<String, QuestSession>> sessions;

    public JournalService(CdrQuestJournalPlugin plugin, QuestRegistry registry, SessionStore store,
                          MessageService messages, QuestAvailabilityService availability) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.messages = messages;
        this.availability = availability;
        this.journalKey = new NamespacedKey(plugin, "quest_journal");
        this.ownerKey = new NamespacedKey(plugin, "journal_owner");
        this.questKey = new NamespacedKey(plugin, "journal_quest");
        this.sessions = store.loadAll();
    }

    public StartResult start(Player player, String questId) {
        Optional<QuestDefinition> definitionOptional = registry.get(questId);
        if (definitionOptional.isEmpty()) {
            messages.send(player, "quest-not-found", Map.of("quest", questId));
            return StartResult.QUEST_NOT_FOUND;
        }
        QuestDefinition definition = definitionOptional.get();
        Map<String, QuestSession> playerSessions = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
        if (playerSessions.containsKey(definition.id().toLowerCase())) {
            messages.send(player, "already-active", Map.of("quest", definition.title()));
            refreshJournal(player, definition.id(), true);
            return StartResult.ALREADY_ACTIVE;
        }
        AvailabilityResult available = availability.check(player.getUniqueId(), definition);
        if (!available.available()) {
            Map<String, String> placeholders = new LinkedHashMap<>(available.placeholders());
            placeholders.put("quest", definition.title());
            messages.send(player, available.messageKey(), placeholders);
            if (playerSessions.isEmpty()) sessions.remove(player.getUniqueId());
            return StartResult.NOT_AVAILABLE;
        }
        int maxActive = Math.max(1, plugin.getConfig().getInt("journal.max-active-quests", 5));
        if (playerSessions.size() >= maxActive) {
            messages.send(player, "max-active");
            return StartResult.MAX_ACTIVE_REACHED;
        }
        if (findJournalSlot(player, definition.id()) < 0 && player.getInventory().firstEmpty() < 0) {
            messages.send(player, "no-space");
            return StartResult.NO_INVENTORY_SPACE;
        }

        long now = Instant.now().getEpochSecond();
        long personalDeadline = availability.personalDeadline(definition, now);
        Map<String, Integer> progress = new LinkedHashMap<>();
        for (String objectiveId : definition.objectives().keySet()) progress.put(objectiveId, 0);
        QuestSession session = new QuestSession(player.getUniqueId(), definition.id(), now, personalDeadline,
                available.cycleEndsAt(), available.cycleKey(),
                definition.objectives().isEmpty() ? QuestStatus.READY : QuestStatus.ACTIVE, progress);
        playerSessions.put(definition.id().toLowerCase(), session);
        store.saveAll(sessions);
        refreshJournal(player, definition.id(), true);
        messages.send(player, "accepted", Map.of("quest", definition.title()));
        if (session.status() == QuestStatus.READY) messages.send(player, "ready", Map.of("giver", definition.giver()));
        return StartResult.STARTED;
    }

    public boolean updateProgress(Player player, String questId, String objectiveId, ProgressMode mode, int amount) {
        QuestSession session = getSession(player.getUniqueId(), questId).orElse(null);
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (session == null || definition == null) return false;
        syncAndExpire(player, definition, session);
        if (session.status() == QuestStatus.EXPIRED) return false;
        ObjectiveDefinition objective = definition.objectives().get(objectiveId);
        if (objective == null) {
            messages.send(player, "objective-not-found", Map.of("objective", objectiveId, "quest", definition.title()));
            return false;
        }
        int oldValue = session.progress(objectiveId);
        int newValue = mode == ProgressMode.ADD ? oldValue + amount : amount;
        newValue = Math.max(0, Math.min(objective.target(), newValue));
        session.progress(objectiveId, newValue);
        QuestStatus before = session.status();
        session.status(allObjectivesComplete(definition, session) ? QuestStatus.READY : QuestStatus.ACTIVE);
        store.saveAll(sessions);
        refreshJournal(player, definition.id(), true);
        messages.send(player, "progress", Map.of("objective", objective.text(), "current", Integer.toString(newValue), "target", Integer.toString(objective.target())));
        if (before != QuestStatus.READY && session.status() == QuestStatus.READY) messages.send(player, "ready", Map.of("giver", definition.giver()));
        return true;
    }

    public boolean isActive(UUID playerId, String questId) {
        QuestSession session = getSession(playerId, questId).orElse(null);
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (session == null || definition == null) return false;
        syncAndExpire(null, definition, session);
        return session.status() != QuestStatus.EXPIRED;
    }

    public boolean isReady(UUID playerId, String questId) {
        QuestSession session = getSession(playerId, questId).orElse(null);
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (session == null || definition == null) return false;
        syncAndExpire(null, definition, session);
        return session.status() == QuestStatus.READY;
    }

    public boolean isExpired(UUID playerId, String questId) {
        QuestSession session = getSession(playerId, questId).orElse(null);
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (session == null || definition == null) return false;
        syncAndExpire(null, definition, session);
        return session.status() == QuestStatus.EXPIRED;
    }

    public boolean isAvailable(UUID playerId, String questId) {
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (definition == null || getSession(playerId, questId).isPresent()) return false;
        return availability.check(playerId, definition).available();
    }

    public boolean isReadyForTurnIn(Player player, String questId) {
        QuestSession session = getSession(player.getUniqueId(), questId).orElse(null);
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (session == null || definition == null) return false;
        syncAndExpire(player, definition, session);
        if (session.status() == QuestStatus.READY) return true;
        messages.send(player, session.status() == QuestStatus.EXPIRED ? "expired" : "not-ready", Map.of("quest", definition.title()));
        return false;
    }

    public boolean hasSession(UUID playerId, String questId) {
        return getSession(playerId, questId).isPresent();
    }

    public String sessionCycleKey(UUID playerId, String questId) {
        return getSession(playerId, questId).map(QuestSession::cycleKey).orElse("");
    }

    public boolean hasJournal(Player player, String questId) {
        return findJournalSlot(player, questId) >= 0;
    }

    public boolean legacyTurnInAllowed() {
        return plugin.getConfig().getBoolean("turn-in.allow-legacy-action", false);
    }

    public void notifyLegacyTurnInDisabled(Player player) {
        messages.send(player, "legacy-turnin-disabled");
    }

    public boolean turnIn(Player player, String questId) {
        if (!isReadyForTurnIn(player, questId)) return false;
        if (!hasJournal(player, questId)) {
            restore(player, questId);
            messages.send(player, "journal-restored-turnin", Map.of("quest", questId));
            return false;
        }
        return commitRewardedTurnIn(player, questId);
    }

    public boolean commitRewardedTurnIn(Player player, String questId) {
        QuestSession session = getSession(player.getUniqueId(), questId).orElse(null);
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (session == null || definition == null) return false;
        availability.recordTerminal(player.getUniqueId(), definition, session, TerminalOutcome.COMPLETED);
        removeJournalItems(player, definition.id());
        removeSession(player.getUniqueId(), definition.id());
        messages.send(player, "turned-in", Map.of("quest", definition.title()));
        return true;
    }

    public boolean fail(Player player, String questId, boolean expired) {
        QuestSession session = getSession(player.getUniqueId(), questId).orElse(null);
        QuestDefinition definition = registry.get(questId).orElse(null);
        if (session == null) return false;
        String displayName = definition == null ? questId : definition.title();
        if (definition != null) availability.recordTerminal(player.getUniqueId(), definition, session, expired ? TerminalOutcome.EXPIRED : TerminalOutcome.FAILED);
        removeJournalItems(player, questId);
        removeSession(player.getUniqueId(), questId);
        messages.send(player, expired ? "expired" : "failed", Map.of("quest", displayName));
        return true;
    }

    public void tickPlayer(Player player) {
        Map<String, QuestSession> playerSessions = sessions.get(player.getUniqueId());
        if (playerSessions == null || playerSessions.isEmpty()) return;
        boolean changed = false;
        for (QuestSession session : List.copyOf(playerSessions.values())) {
            QuestDefinition definition = registry.get(session.questId()).orElse(null);
            if (definition == null) continue;
            QuestStatus before = session.status();
            long beforeLifecycle = session.lifecycleEndsAt();
            syncAndExpire(player, definition, session);
            if (before != session.status() || beforeLifecycle != session.lifecycleEndsAt()) changed = true;
            refreshJournal(player, definition.id(), true);
        }
        if (changed) store.saveAll(sessions);
    }

    public void restorePlayer(Player player) {
        Map<String, QuestSession> playerSessions = sessions.get(player.getUniqueId());
        if (playerSessions == null || playerSessions.isEmpty()) return;
        for (QuestSession session : playerSessions.values()) {
            registry.get(session.questId()).ifPresent(definition -> {
                syncAndExpire(player, definition, session);
                refreshJournal(player, definition.id(), true);
            });
        }
        store.saveAll(sessions);
    }

    public boolean restore(Player player, String questId) {
        if (questId == null) {
            restorePlayer(player);
            return hasAnySession(player.getUniqueId());
        }
        if (getSession(player.getUniqueId(), questId).isEmpty()) return false;
        return refreshJournal(player, questId, true);
    }

    public void refreshQuestForOnlinePlayers(String questId) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            QuestSession session = getSession(player.getUniqueId(), questId).orElse(null);
            QuestDefinition definition = registry.get(questId).orElse(null);
            if (session == null || definition == null) continue;
            syncAndExpire(player, definition, session);
            refreshJournal(player, questId, true);
        }
        store.saveAll(sessions);
    }

    public Collection<QuestSession> sessions(UUID playerId) {
        Map<String, QuestSession> playerSessions = sessions.get(playerId);
        return playerSessions == null ? List.of() : List.copyOf(playerSessions.values());
    }

    public Optional<QuestDefinition> definition(String questId) { return registry.get(questId); }

    public boolean isJournal(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(journalKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isJournalOwnedBy(ItemStack item, UUID playerId) {
        if (!isJournal(item)) return false;
        String owner = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return playerId.toString().equals(owner);
    }

    public String journalQuestId(ItemStack item) {
        if (!isJournal(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(questKey, PersistentDataType.STRING);
    }

    public void removeJournalFromDrops(List<ItemStack> drops, UUID playerId) {
        drops.removeIf(item -> isJournalOwnedBy(item, playerId));
    }

    public void save() { store.saveAll(sessions); }

    public long remainingSeconds(QuestSession session) { return session.remainingSeconds(Instant.now().getEpochSecond()); }

    private boolean hasAnySession(UUID playerId) {
        Map<String, QuestSession> playerSessions = sessions.get(playerId);
        return playerSessions != null && !playerSessions.isEmpty();
    }

    private Optional<QuestSession> getSession(UUID playerId, String questId) {
        Map<String, QuestSession> playerSessions = sessions.get(playerId);
        if (playerSessions == null || questId == null) return Optional.empty();
        return Optional.ofNullable(playerSessions.get(questId.toLowerCase()));
    }

    private void removeSession(UUID playerId, String questId) {
        Map<String, QuestSession> playerSessions = sessions.get(playerId);
        if (playerSessions == null) return;
        playerSessions.remove(questId.toLowerCase());
        if (playerSessions.isEmpty()) sessions.remove(playerId);
        store.saveAll(sessions);
    }

    private boolean allObjectivesComplete(QuestDefinition definition, QuestSession session) {
        for (ObjectiveDefinition objective : definition.objectives().values()) {
            if (session.progress(objective.id()) < objective.target()) return false;
        }
        return true;
    }

    private boolean isExpiredByClock(QuestSession session) {
        return session.hasDeadline() && Instant.now().getEpochSecond() >= session.effectiveExpiresAt();
    }

    private void syncAndExpire(Player player, QuestDefinition definition, QuestSession session) {
        availability.syncLifecycleEnd(definition, session);
        if (session.status() == QuestStatus.EXPIRED || !isExpiredByClock(session)) return;
        session.status(QuestStatus.EXPIRED);
        availability.recordTerminal(session.playerId(), definition, session, TerminalOutcome.EXPIRED);
        store.saveAll(sessions);
        if (player != null) messages.send(player, "expired", Map.of("quest", definition.title()));
    }

    private boolean refreshJournal(Player player, String questId, boolean giveIfMissing) {
        QuestDefinition definition = registry.get(questId).orElse(null);
        QuestSession session = getSession(player.getUniqueId(), questId).orElse(null);
        if (definition == null || session == null) return false;
        ItemStack book = createBook(player, definition, session);
        int slot = findJournalSlot(player, definition.id());
        if (slot >= 0) {
            player.getInventory().setItem(slot, book);
            return true;
        }
        if (!giveIfMissing) return false;
        int empty = player.getInventory().firstEmpty();
        if (empty < 0) {
            messages.send(player, "no-space");
            return false;
        }
        player.getInventory().setItem(empty, book);
        return true;
    }

    private int findJournalSlot(Player player, String questId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isJournalOwnedBy(item, player.getUniqueId())) continue;
            String itemQuest = journalQuestId(item);
            if (itemQuest != null && itemQuest.equalsIgnoreCase(questId)) return i;
        }
        return -1;
    }

    private void removeJournalItems(Player player, String questId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isJournalOwnedBy(item, player.getUniqueId())) continue;
            String itemQuest = journalQuestId(item);
            if (itemQuest != null && itemQuest.equalsIgnoreCase(questId)) player.getInventory().setItem(i, null);
        }
    }

    private ItemStack createBook(Player player, QuestDefinition definition, QuestSession session) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        String title = definition.title();
        if (title.length() > 32) title = title.substring(0, 32);
        meta.setTitle(title);
        meta.setAuthor(plugin.getConfig().getString("journal.author", "MoonSign S2"));
        meta.setPages(buildPages(definition, session));
        meta.getPersistentDataContainer().set(journalKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        meta.getPersistentDataContainer().set(questKey, PersistentDataType.STRING, definition.id());
        book.setItemMeta(meta);
        return book;
    }

    private List<String> buildPages(QuestDefinition definition, QuestSession session) {
        List<String> pages = new ArrayList<>();
        String status = switch (session.status()) {
            case ACTIVE -> ChatColor.DARK_GREEN + "ACTIVE";
            case READY -> ChatColor.GREEN + "RETURN TO NPC";
            case EXPIRED -> ChatColor.DARK_RED + "EXPIRED";
        };
        StringBuilder summary = new StringBuilder();
        summary.append(ChatColor.DARK_GRAY).append(ChatColor.BOLD).append("QUEST JOURNAL\n\n");
        summary.append(ChatColor.DARK_BLUE).append(ChatColor.BOLD).append(definition.title()).append("\n");
        summary.append(ChatColor.GRAY).append("Type: ").append(ChatColor.BLACK).append(definition.type().name()).append("\n\n");
        summary.append(ChatColor.BLACK).append("Quest Giver:\n").append(ChatColor.DARK_GRAY).append(definition.giver()).append("\n\n");
        summary.append(ChatColor.BLACK).append("Status:\n").append(status).append("\n\n");
        summary.append(ChatColor.BLACK).append("Time:\n").append(ChatColor.DARK_RED).append(formatRemaining(session));
        pages.add(summary.toString());

        if (definition.type() == QuestType.DAILY || definition.type() == QuestType.LIMITED) {
            StringBuilder lifecycle = new StringBuilder(ChatColor.GOLD + "" + ChatColor.BOLD + "AVAILABILITY\n\n");
            if (definition.type() == QuestType.DAILY) {
                lifecycle.append(ChatColor.BLACK).append("Daily Reset:\n").append(ChatColor.DARK_GRAY)
                        .append(availability.dailyReset()).append(" ").append(availability.zoneId().getId()).append("\n\n");
                lifecycle.append(ChatColor.BLACK).append("Cycle Ends:\n").append(ChatColor.DARK_GRAY)
                        .append(availability.formatAt(session.lifecycleEndsAt()));
            } else {
                lifecycle.append(ChatColor.BLACK).append("Limited Event\n\n");
                lifecycle.append(ChatColor.BLACK).append("Event Ends:\n").append(ChatColor.DARK_RED)
                        .append(availability.formatAt(session.lifecycleEndsAt()));
            }
            pages.add(lifecycle.toString());
        }

        if (!definition.description().isEmpty()) {
            StringBuilder description = new StringBuilder(ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "MISSION\n\n");
            for (String line : definition.description()) description.append(ChatColor.BLACK).append(line).append("\n");
            pages.add(description.toString());
        }

        StringBuilder objectives = new StringBuilder(ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "OBJECTIVES\n\n");
        if (definition.objectives().isEmpty()) objectives.append(ChatColor.DARK_GREEN).append("✓ No tracked objectives\n");
        else for (ObjectiveDefinition objective : definition.objectives().values()) {
            int current = session.progress(objective.id());
            boolean done = current >= objective.target();
            objectives.append(done ? ChatColor.DARK_GREEN + "✓ " : ChatColor.DARK_GRAY + "□ ");
            objectives.append(ChatColor.BLACK).append(objective.text()).append("\n");
            objectives.append(ChatColor.GRAY).append("   ").append(current).append("/").append(objective.target()).append("\n\n");
        }
        pages.add(objectives.toString());

        StringBuilder rewards = new StringBuilder(ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "REWARDS\n\n");
        if (definition.rewards().isEmpty()) rewards.append(ChatColor.GRAY).append("No listed rewards.\n");
        else for (String reward : definition.rewards()) rewards.append(ChatColor.DARK_GREEN).append("• ").append(ChatColor.BLACK).append(reward).append("\n");
        if (!definition.failure().isEmpty()) {
            rewards.append("\n").append(ChatColor.DARK_RED).append(ChatColor.BOLD).append("FAILURE\n");
            for (String line : definition.failure()) rewards.append(ChatColor.RED).append("• ").append(line).append("\n");
        }
        if (!definition.expiration().isEmpty()) {
            rewards.append("\n").append(ChatColor.DARK_RED).append(ChatColor.BOLD).append("EXPIRED\n");
            for (String line : definition.expiration()) rewards.append(ChatColor.RED).append("• ").append(line).append("\n");
        }
        pages.add(rewards.toString());

        if (session.status() == QuestStatus.READY) {
            pages.add(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "OBJECTIVES COMPLETE\n\n"
                    + ChatColor.BLACK + "Return to:\n" + ChatColor.DARK_BLUE + definition.giver() + "\n\n"
                    + ChatColor.BLACK + "Hand this journal to the quest giver to receive your reward.");
        } else if (session.status() == QuestStatus.EXPIRED) {
            pages.add(ChatColor.DARK_RED + "" + ChatColor.BOLD + "QUEST EXPIRED\n\n"
                    + ChatColor.BLACK + "This journal can no longer be turned in.");
        }
        return pages;
    }

    private String formatRemaining(QuestSession session) {
        if (!session.hasDeadline()) return "No time limit";
        long remaining = session.remainingSeconds(Instant.now().getEpochSecond());
        long hours = remaining / 3600;
        long minutes = (remaining % 3600) / 60;
        long seconds = remaining % 60;
        return hours > 0 ? "%dh %02dm %02ds".formatted(hours, minutes, seconds) : "%02dm %02ds".formatted(minutes, seconds);
    }
}
