package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.ObjectiveDefinition;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.model.QuestStatus;
import com.menkiestes.cdrquestjournal.model.QuestType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class JournalUiService {
    private final CdrQuestJournalPlugin plugin;
    private final JournalService journals;
    private final QuestAvailabilityService availability;
    private final NpcBindingService bindings;

    public JournalUiService(CdrQuestJournalPlugin plugin, JournalService journals,
                            QuestAvailabilityService availability, NpcBindingService bindings) {
        this.plugin = plugin;
        this.journals = journals;
        this.availability = availability;
        this.bindings = bindings;
    }

    public void refreshAll(Player player) {
        if (!enabled()) return;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            if (!journals.isJournalOwnedBy(item, player.getUniqueId())) continue;
            player.getInventory().setItem(i, render(player, item));
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (journals.isJournalOwnedBy(offhand, player.getUniqueId())) {
            player.getInventory().setItemInOffHand(render(player, offhand));
        }
    }

    public void refreshHand(Player player, EquipmentSlot hand) {
        if (!enabled() || hand == null) return;
        ItemStack item = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!journals.isJournalOwnedBy(item, player.getUniqueId())) return;
        ItemStack rendered = render(player, item);
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(rendered);
        else player.getInventory().setItemInMainHand(rendered);
    }

    public ItemStack render(Player player, ItemStack original) {
        if (!enabled() || original == null || original.getType() != Material.WRITTEN_BOOK) return original;
        String questId = journals.journalQuestId(original);
        if (questId == null) return original;
        QuestDefinition definition = journals.definition(questId).orElse(null);
        QuestSession session = journals.sessions(player.getUniqueId()).stream()
                .filter(candidate -> candidate.questId().equalsIgnoreCase(questId))
                .findFirst().orElse(null);
        if (definition == null || session == null) return original;

        ItemStack book = original.clone();
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return original;

        String title = definition.title();
        if (title.length() > 32) title = title.substring(0, 32);
        meta.setTitle(title);
        meta.setDisplayName(color(itemNameTemplate()
                .replace("%quest%", definition.title())
                .replace("%type%", definition.type().name())));
        meta.setLore(List.of(
                typeColor(definition.type()) + "[" + definition.type().name() + "] " + ChatColor.GRAY + "Quest Journal",
                ChatColor.GRAY + "Status: " + statusColor(session.status()) + statusLabel(session.status()),
                ChatColor.DARK_GRAY + "Right-click to read"
        ));
        meta.setPages(renderPages(definition, session));
        book.setItemMeta(meta);
        return book;
    }

    private List<String> renderPages(QuestDefinition definition, QuestSession session) {
        List<String> pages = new ArrayList<>();
        Progress progress = progress(definition, session);
        String giver = bindings.binding(definition.id()).map(binding -> binding.npcName()).orElse(definition.giver());

        StringBuilder cover = new StringBuilder();
        cover.append(ChatColor.DARK_GRAY).append(ChatColor.BOLD).append("QUEST JOURNAL\n");
        cover.append(ChatColor.GRAY).append(divider()).append("\n\n");
        cover.append(typeColor(definition.type())).append(ChatColor.BOLD)
                .append("[").append(definition.type().name()).append("]\n");
        cover.append(ChatColor.DARK_BLUE).append(ChatColor.BOLD).append(definition.title()).append("\n\n");
        cover.append(ChatColor.DARK_GRAY).append("Quest Giver\n").append(ChatColor.BLACK).append(giver).append("\n\n");
        cover.append(ChatColor.DARK_GRAY).append("Status\n").append(statusColor(session.status()))
                .append(ChatColor.BOLD).append(statusLabel(session.status())).append("\n\n");
        cover.append(ChatColor.DARK_GRAY).append("Time Left\n").append(timeColor(session))
                .append(formatRemaining(session));
        pages.add(cover.toString());

        StringBuilder overview = new StringBuilder();
        overview.append(ChatColor.DARK_BLUE).append(ChatColor.BOLD).append("MISSION OVERVIEW\n");
        overview.append(ChatColor.GRAY).append(divider()).append("\n\n");
        if (definition.description().isEmpty()) {
            overview.append(ChatColor.GRAY).append("No mission description.\n\n");
        } else {
            for (String line : definition.description()) overview.append(ChatColor.BLACK).append(line).append("\n");
            overview.append("\n");
        }
        overview.append(ChatColor.DARK_GRAY).append("Overall Progress\n");
        overview.append(progressBar(progress.percent())).append("\n");
        overview.append(ChatColor.DARK_GREEN).append(progress.percent()).append("% ")
                .append(ChatColor.GRAY).append("(").append(progress.current()).append("/").append(progress.target()).append(")");
        pages.add(overview.toString());

        addObjectivePages(pages, definition, session);
        addRewardPages(pages, definition);
        addLifecyclePage(pages, definition, session);
        addFinalStatusPage(pages, definition, session, giver);
        return pages;
    }

    private void addObjectivePages(List<String> pages, QuestDefinition definition, QuestSession session) {
        List<ObjectiveDefinition> objectives = new ArrayList<>(definition.objectives().values());
        int perPage = clamp(plugin.getConfig().getInt("journal-ui.objectives-per-page", 4), 2, 6);
        if (objectives.isEmpty()) {
            pages.add(ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "OBJECTIVES\n"
                    + ChatColor.GRAY + divider() + "\n\n"
                    + doneMarker(true) + ChatColor.DARK_GREEN + " No tracked objectives.");
            return;
        }

        for (int start = 0; start < objectives.size(); start += perPage) {
            int end = Math.min(objectives.size(), start + perPage);
            StringBuilder page = new StringBuilder();
            page.append(ChatColor.DARK_BLUE).append(ChatColor.BOLD).append("OBJECTIVES")
                    .append(objectives.size() > perPage ? " " + (start / perPage + 1) : "").append("\n");
            page.append(ChatColor.GRAY).append(divider()).append("\n\n");
            for (int i = start; i < end; i++) {
                ObjectiveDefinition objective = objectives.get(i);
                int current = Math.min(objective.target(), session.progress(objective.id()));
                boolean complete = current >= objective.target();
                page.append(doneMarker(complete))
                        .append(complete ? ChatColor.DARK_GREEN : ChatColor.BLACK)
                        .append(" ").append(objective.text()).append("\n");
                page.append(ChatColor.GRAY).append("   ").append(current).append("/").append(objective.target());
                if (complete) page.append(ChatColor.DARK_GREEN).append("  COMPLETE");
                page.append("\n\n");
            }
            pages.add(page.toString());
        }
    }

    private void addRewardPages(List<String> pages, QuestDefinition definition) {
        StringBuilder rewards = new StringBuilder();
        rewards.append(ChatColor.DARK_GREEN).append(ChatColor.BOLD).append("REWARD PREVIEW\n");
        rewards.append(ChatColor.GRAY).append(divider()).append("\n\n");
        if (definition.rewards().isEmpty()) {
            rewards.append(ChatColor.GRAY).append("No listed rewards.");
        } else {
            for (String reward : definition.rewards()) {
                rewards.append(ChatColor.DARK_GREEN).append("+ ").append(ChatColor.BLACK).append(reward).append("\n");
            }
        }
        pages.add(rewards.toString());

        if (!definition.failure().isEmpty() || !definition.expiration().isEmpty()) {
            StringBuilder consequence = new StringBuilder();
            consequence.append(ChatColor.DARK_RED).append(ChatColor.BOLD).append("CONSEQUENCES\n");
            consequence.append(ChatColor.GRAY).append(divider()).append("\n\n");
            if (!definition.failure().isEmpty()) {
                consequence.append(ChatColor.DARK_RED).append(ChatColor.BOLD).append("FAILED\n");
                for (String line : definition.failure()) consequence.append(ChatColor.RED).append("- ").append(line).append("\n");
                consequence.append("\n");
            }
            if (!definition.expiration().isEmpty()) {
                consequence.append(ChatColor.DARK_RED).append(ChatColor.BOLD).append("EXPIRED\n");
                for (String line : definition.expiration()) consequence.append(ChatColor.RED).append("- ").append(line).append("\n");
            }
            pages.add(consequence.toString());
        }
    }

    private void addLifecyclePage(List<String> pages, QuestDefinition definition, QuestSession session) {
        boolean showAccepted = plugin.getConfig().getBoolean("journal-ui.show-accepted-at", true);
        boolean showCycle = plugin.getConfig().getBoolean("journal-ui.show-cycle-info", true);
        if (!showAccepted && (!showCycle || definition.type() == QuestType.STORY)) return;

        StringBuilder lifecycle = new StringBuilder();
        lifecycle.append(typeColor(definition.type())).append(ChatColor.BOLD).append("QUEST TIMING\n");
        lifecycle.append(ChatColor.GRAY).append(divider()).append("\n\n");
        if (showAccepted) {
            lifecycle.append(ChatColor.DARK_GRAY).append("Accepted\n")
                    .append(ChatColor.BLACK).append(availability.formatAt(session.acceptedAt())).append("\n\n");
        }
        if (showCycle && definition.type() == QuestType.DAILY) {
            lifecycle.append(ChatColor.DARK_GRAY).append("Daily Reset\n")
                    .append(ChatColor.BLACK).append(availability.dailyReset()).append(" ")
                    .append(availability.zoneId().getId()).append("\n\n");
            lifecycle.append(ChatColor.DARK_GRAY).append("Cycle Ends\n")
                    .append(ChatColor.DARK_RED).append(availability.formatAt(session.lifecycleEndsAt()));
        } else if (showCycle && definition.type() == QuestType.LIMITED) {
            lifecycle.append(ChatColor.DARK_GRAY).append("Event Ends\n")
                    .append(ChatColor.DARK_RED).append(availability.formatAt(session.lifecycleEndsAt())).append("\n\n");
            lifecycle.append(ChatColor.GRAY).append("The event deadline overrides a longer personal timer.");
        }
        pages.add(lifecycle.toString());
    }

    private void addFinalStatusPage(List<String> pages, QuestDefinition definition, QuestSession session, String giver) {
        if (session.status() == QuestStatus.READY) {
            StringBuilder ready = new StringBuilder();
            ready.append(ChatColor.DARK_GREEN).append(ChatColor.BOLD).append("QUEST COMPLETE\n");
            ready.append(ChatColor.GRAY).append(divider()).append("\n\n");
            ready.append(ChatColor.BLACK).append("All objectives are complete.\n\n");
            ready.append(ChatColor.DARK_GRAY).append("Return to\n").append(ChatColor.DARK_BLUE)
                    .append(ChatColor.BOLD).append(giver).append("\n\n");
            ready.append(ChatColor.BLACK).append("Keep this journal in your inventory and hand it to the quest giver to claim the reward.");
            pages.add(ready.toString());
        } else if (session.status() == QuestStatus.EXPIRED) {
            pages.add(ChatColor.DARK_RED + "" + ChatColor.BOLD + "QUEST EXPIRED\n"
                    + ChatColor.GRAY + divider() + "\n\n"
                    + ChatColor.BLACK + "The deadline has passed. This journal can no longer be turned in for a reward.");
        }
    }

    private Progress progress(QuestDefinition definition, QuestSession session) {
        if (definition.objectives().isEmpty()) return new Progress(1, 1, 100);
        int current = 0;
        int target = 0;
        for (ObjectiveDefinition objective : definition.objectives().values()) {
            target += objective.target();
            current += Math.min(objective.target(), session.progress(objective.id()));
        }
        int percent = target <= 0 ? 100 : (int) Math.round(current * 100.0 / target);
        return new Progress(current, target, Math.max(0, Math.min(100, percent)));
    }

    private String progressBar(int percent) {
        int width = clamp(plugin.getConfig().getInt("journal-ui.progress-bar-width", 12), 5, 20);
        int filled = (int) Math.round(width * (percent / 100.0));
        String filledChar = safeMode() ? "#" : "■";
        String emptyChar = safeMode() ? "-" : "□";
        return ChatColor.DARK_GRAY + "[" + ChatColor.DARK_GREEN
                + filledChar.repeat(Math.max(0, filled))
                + ChatColor.GRAY + emptyChar.repeat(Math.max(0, width - filled))
                + ChatColor.DARK_GRAY + "]";
    }

    private String doneMarker(boolean done) {
        if (safeMode()) return done ? ChatColor.DARK_GREEN + "[x]" : ChatColor.DARK_GRAY + "[ ]";
        return done ? ChatColor.DARK_GREEN + "✓" : ChatColor.DARK_GRAY + "□";
    }

    private String divider() {
        return safeMode() ? "----------------" : "──────────────";
    }

    private String formatRemaining(QuestSession session) {
        if (!session.hasDeadline()) return "No time limit";
        long remaining = Math.max(0L, session.remainingSeconds(Instant.now().getEpochSecond()));
        long days = remaining / 86400;
        long hours = (remaining % 86400) / 3600;
        long minutes = (remaining % 3600) / 60;
        long seconds = remaining % 60;
        if (days > 0) return "%dd %02dh %02dm".formatted(days, hours, minutes);
        if (hours > 0) return "%dh %02dm %02ds".formatted(hours, minutes, seconds);
        return "%02dm %02ds".formatted(minutes, seconds);
    }

    private ChatColor timeColor(QuestSession session) {
        if (!session.hasDeadline()) return ChatColor.DARK_GREEN;
        long remaining = session.remainingSeconds(Instant.now().getEpochSecond());
        if (remaining <= 300) return ChatColor.DARK_RED;
        if (remaining <= 900) return ChatColor.GOLD;
        return ChatColor.DARK_GREEN;
    }

    private ChatColor typeColor(QuestType type) {
        return switch (type) {
            case STORY -> ChatColor.DARK_AQUA;
            case DAILY -> ChatColor.GOLD;
            case LIMITED -> ChatColor.LIGHT_PURPLE;
        };
    }

    private ChatColor statusColor(QuestStatus status) {
        return switch (status) {
            case ACTIVE -> ChatColor.DARK_GREEN;
            case READY -> ChatColor.GOLD;
            case EXPIRED -> ChatColor.DARK_RED;
        };
    }

    private String statusLabel(QuestStatus status) {
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case READY -> "RETURN TO NPC";
            case EXPIRED -> "EXPIRED";
        };
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("journal-ui.enabled", true);
    }

    private boolean safeMode() {
        return plugin.getConfig().getBoolean("journal-ui.crossplay-safe", true);
    }

    private String itemNameTemplate() {
        return plugin.getConfig().getString("journal-ui.item-name", "&6&lQuest Journal &8• &f%quest%");
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Progress(int current, int target, int percent) {}
}
