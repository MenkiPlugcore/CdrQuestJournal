package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.AvailabilityResult;
import com.menkiestes.cdrquestjournal.model.LimitedQuestWindow;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestHistoryEntry;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.model.QuestType;
import com.menkiestes.cdrquestjournal.model.TerminalOutcome;
import com.menkiestes.cdrquestjournal.storage.LifecycleStore;
import com.menkiestes.cdrquestjournal.storage.LimitedScheduleStore;
import com.menkiestes.cdrquestjournal.storage.QuestHistoryStore;
import com.menkiestes.cdrquestjournal.storage.StoryProgressionStore;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestAvailabilityService {
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CdrQuestJournalPlugin plugin;
    private final LimitedScheduleStore limitedStore;
    private final LifecycleStore lifecycleStore;
    private final StoryProgressionStore storyStore;
    private final QuestHistoryStore historyStore;
    private volatile ZoneId zoneId;
    private volatile LocalTime dailyReset;

    public QuestAvailabilityService(
            CdrQuestJournalPlugin plugin,
            LimitedScheduleStore limitedStore,
            LifecycleStore lifecycleStore
    ) {
        this.plugin = plugin;
        this.limitedStore = limitedStore;
        this.lifecycleStore = lifecycleStore;
        this.storyStore = new StoryProgressionStore(plugin);
        this.historyStore = new QuestHistoryStore(plugin);
        reload();
    }

    public void reload() {
        String zoneRaw = plugin.getConfig().getString("lifecycle.timezone", "Asia/Jakarta");
        try {
            zoneId = ZoneId.of(zoneRaw);
        } catch (DateTimeException ex) {
            zoneId = ZoneId.of("Asia/Jakarta");
            plugin.getLogger().warning("Invalid lifecycle.timezone '" + zoneRaw + "', using Asia/Jakarta.");
        }

        String resetRaw = plugin.getConfig().getString("lifecycle.daily-reset", "00:00");
        try {
            dailyReset = LocalTime.parse(resetRaw);
        } catch (DateTimeException ex) {
            dailyReset = LocalTime.MIDNIGHT;
            plugin.getLogger().warning("Invalid lifecycle.daily-reset '" + resetRaw + "', using 00:00.");
        }
        limitedStore.reload();
        storyStore.reload();
        historyStore.reload();
    }

    public AvailabilityResult check(UUID playerId, QuestDefinition definition) {
        long now = Instant.now().getEpochSecond();
        if (definition.type() == QuestType.STORY) {
            if (!definition.storyRepeatable() && storyStore.isCompleted(playerId, definition.id())) {
                return AvailabilityResult.denied("story-completed", "STORY", 0L, Map.of());
            }

            List<String> missingAll = new ArrayList<>();
            for (String required : definition.requiresAll()) {
                if (!storyStore.isCompleted(playerId, required)) missingAll.add(displayQuest(required));
            }
            if (!missingAll.isEmpty()) {
                return AvailabilityResult.denied(
                        "story-locked",
                        "STORY",
                        0L,
                        Map.of("requirements", String.join(", ", missingAll))
                );
            }

            if (!definition.requiresAny().isEmpty()) {
                boolean anyCompleted = definition.requiresAny().stream().anyMatch(id -> storyStore.isCompleted(playerId, id));
                if (!anyCompleted) {
                    List<String> alternatives = definition.requiresAny().stream().map(this::displayQuest).toList();
                    return AvailabilityResult.denied(
                            "story-locked-any",
                            "STORY",
                            0L,
                            Map.of("requirements", String.join(" / ", alternatives))
                    );
                }
            }
            AvailabilityResult cooldown = cooldownResult(playerId, definition, now);
            return cooldown == null ? AvailabilityResult.available("STORY", 0L) : cooldown;
        }

        if (definition.type() == QuestType.DAILY) {
            Cycle cycle = dailyCycle(Instant.ofEpochSecond(now).atZone(zoneId));
            if (lifecycleStore.isLocked(playerId, definition.id(), cycle.key())) {
                return AvailabilityResult.denied(
                        "daily-locked",
                        cycle.key(),
                        cycle.endAt(),
                        Map.of("reset", formatAt(cycle.endAt()))
                );
            }
            AvailabilityResult cooldown = cooldownResult(playerId, definition, now);
            return cooldown == null ? AvailabilityResult.available(cycle.key(), cycle.endAt()) : cooldown;
        }

        Optional<LimitedQuestWindow> optional = limitedStore.get(definition.id());
        if (optional.isEmpty()) {
            return AvailabilityResult.denied("limited-not-configured", "", 0L, Map.of());
        }
        LimitedQuestWindow window = optional.get();
        if (now < window.startAt()) {
            return AvailabilityResult.denied(
                    "limited-not-started",
                    window.cycleKey(),
                    window.endAt(),
                    Map.of("start", formatAt(window.startAt()))
            );
        }
        if (now >= window.endAt()) {
            return AvailabilityResult.denied(
                    "limited-ended",
                    window.cycleKey(),
                    window.endAt(),
                    Map.of("end", formatAt(window.endAt()))
            );
        }
        if (lifecycleStore.isLocked(playerId, definition.id(), window.cycleKey())) {
            return AvailabilityResult.denied(
                    "limited-locked",
                    window.cycleKey(),
                    window.endAt(),
                    Map.of("end", formatAt(window.endAt()))
            );
        }
        AvailabilityResult cooldown = cooldownResult(playerId, definition, now);
        return cooldown == null ? AvailabilityResult.available(window.cycleKey(), window.endAt()) : cooldown;
    }

    public long personalDeadline(QuestDefinition definition, long acceptedAt) {
        return definition.timeLimitSeconds() > 0L ? acceptedAt + definition.timeLimitSeconds() : 0L;
    }

    public void syncLifecycleEnd(QuestDefinition definition, QuestSession session) {
        if (definition.type() == QuestType.STORY) {
            session.lifecycleEndsAt(0L);
            return;
        }
        if (definition.type() == QuestType.DAILY) {
            if (session.lifecycleEndsAt() <= 0L) {
                Cycle cycle = dailyCycle(Instant.ofEpochSecond(session.acceptedAt()).atZone(zoneId));
                session.lifecycleEndsAt(cycle.endAt());
            }
            return;
        }

        LimitedQuestWindow window = limitedStore.get(definition.id()).orElse(null);
        if (window == null || !window.cycleKey().equals(session.cycleKey())) {
            session.lifecycleEndsAt(Instant.now().getEpochSecond());
            return;
        }
        session.lifecycleEndsAt(window.endAt());
    }

    public void recordTerminal(UUID playerId, QuestDefinition definition, QuestSession session, TerminalOutcome outcome) {
        long endedAt = Instant.now().getEpochSecond();
        historyStore.record(new QuestHistoryEntry(
                playerId,
                definition.id(),
                definition.type(),
                session.acceptedAt(),
                endedAt,
                outcome,
                definition.giver(),
                session.cycleKey()
        ));

        if (definition.type() == QuestType.STORY) {
            if (outcome == TerminalOutcome.COMPLETED && !definition.storyRepeatable()) {
                storyStore.complete(playerId, definition.id(), endedAt);
            }
            return;
        }
        if (definition.type() == QuestType.DAILY || definition.type() == QuestType.LIMITED) {
            lifecycleStore.record(playerId, definition.id(), session.cycleKey(), outcome);
        }
    }

    public List<QuestHistoryEntry> history(UUID playerId) {
        return historyStore.history(playerId);
    }

    public boolean historyCompleted(UUID playerId, String questId) {
        return historyStore.hasCompleted(playerId, questId);
    }

    public int historyCount(UUID playerId) {
        return historyStore.count(playerId);
    }

    public long cooldownRemaining(UUID playerId, QuestDefinition definition) {
        if (definition == null || definition.cooldownSeconds() <= 0L) return 0L;
        long now = Instant.now().getEpochSecond();
        return historyStore.latest(playerId, definition.id())
                .map(entry -> Math.max(0L, entry.endedAt() + definition.cooldownSeconds() - now))
                .orElse(0L);
    }

    public boolean cooldownReady(UUID playerId, String questId) {
        QuestDefinition definition = plugin.getQuestRegistry().get(questId).orElse(null);
        return definition != null && cooldownRemaining(playerId, definition) <= 0L;
    }

    public boolean isStoryCompleted(UUID playerId, String questId) {
        return storyStore.isCompleted(playerId, questId);
    }

    public long storyCompletedAt(UUID playerId, String questId) {
        return storyStore.completedAt(playerId, questId);
    }

    public int storyCompletedCount(UUID playerId) {
        return storyStore.completedCount(playerId);
    }

    public boolean resetStory(UUID playerId, String questId) {
        return storyStore.reset(playerId, questId);
    }

    public boolean resetAllStories(UUID playerId) {
        return storyStore.resetAll(playerId);
    }

    public Optional<LimitedQuestWindow> limitedWindow(String questId) {
        return limitedStore.get(questId);
    }

    public void setLimitedWindow(String questId, long startAt, long endAt) {
        limitedStore.set(questId, startAt, endAt);
    }

    public boolean updateLimitedEnd(String questId, long endAt) {
        return limitedStore.updateEnd(questId, endAt);
    }

    public boolean clearLimitedWindow(String questId) {
        return limitedStore.clear(questId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public LocalTime dailyReset() {
        return dailyReset;
    }

    public String formatAt(long epochSecond) {
        if (epochSecond <= 0L) return "-";
        return DISPLAY_FORMAT.format(Instant.ofEpochSecond(epochSecond).atZone(zoneId)) + " " + zoneId.getId();
    }

    public long nextDailyResetEpoch() {
        return dailyCycle(ZonedDateTime.now(zoneId)).endAt();
    }

    private AvailabilityResult cooldownResult(UUID playerId, QuestDefinition definition, long now) {
        if (definition.cooldownSeconds() <= 0L) return null;
        QuestHistoryEntry latest = historyStore.latest(playerId, definition.id()).orElse(null);
        if (latest == null) return null;
        long readyAt = latest.endedAt() + definition.cooldownSeconds();
        if (now >= readyAt) return null;
        return AvailabilityResult.denied(
                "cooldown-active",
                "COOLDOWN",
                readyAt,
                Map.of(
                        "remaining", formatDuration(readyAt - now),
                        "ready", formatAt(readyAt)
                )
        );
    }

    private String displayQuest(String questId) {
        return plugin.getQuestRegistry().get(questId).map(QuestDefinition::title).orElse(questId);
    }

    private static String formatDuration(long seconds) {
        seconds = Math.max(0L, seconds);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (days > 0) return "%dd %02dh %02dm".formatted(days, hours, minutes);
        if (hours > 0) return "%dh %02dm %02ds".formatted(hours, minutes, secs);
        return "%02dm %02ds".formatted(minutes, secs);
    }

    private Cycle dailyCycle(ZonedDateTime now) {
        ZonedDateTime resetToday = now.toLocalDate().atTime(dailyReset).atZone(zoneId);
        ZonedDateTime start = now.isBefore(resetToday) ? resetToday.minusDays(1) : resetToday;
        ZonedDateTime end = start.plusDays(1);
        long startAt = start.toEpochSecond();
        return new Cycle("DAILY:" + startAt, startAt, end.toEpochSecond());
    }

    private record Cycle(String key, long startAt, long endAt) {}
}
