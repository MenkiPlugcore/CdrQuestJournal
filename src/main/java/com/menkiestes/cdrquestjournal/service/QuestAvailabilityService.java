package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.AvailabilityResult;
import com.menkiestes.cdrquestjournal.model.LimitedQuestWindow;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.model.QuestType;
import com.menkiestes.cdrquestjournal.model.TerminalOutcome;
import com.menkiestes.cdrquestjournal.storage.LifecycleStore;
import com.menkiestes.cdrquestjournal.storage.LimitedScheduleStore;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestAvailabilityService {
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CdrQuestJournalPlugin plugin;
    private final LimitedScheduleStore limitedStore;
    private final LifecycleStore lifecycleStore;
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
    }

    public AvailabilityResult check(UUID playerId, QuestDefinition definition) {
        long now = Instant.now().getEpochSecond();
        if (definition.type() == QuestType.STORY) {
            return AvailabilityResult.available("STORY", 0L);
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
            return AvailabilityResult.available(cycle.key(), cycle.endAt());
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
        return AvailabilityResult.available(window.cycleKey(), window.endAt());
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
        if (definition.type() == QuestType.DAILY || definition.type() == QuestType.LIMITED) {
            lifecycleStore.record(playerId, definition.id(), session.cycleKey(), outcome);
        }
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

    private Cycle dailyCycle(ZonedDateTime now) {
        ZonedDateTime resetToday = now.toLocalDate().atTime(dailyReset).atZone(zoneId);
        ZonedDateTime start = now.isBefore(resetToday) ? resetToday.minusDays(1) : resetToday;
        ZonedDateTime end = start.plusDays(1);
        long startAt = start.toEpochSecond();
        return new Cycle("DAILY:" + startAt, startAt, end.toEpochSecond());
    }

    private record Cycle(String key, long startAt, long endAt) {}
}
