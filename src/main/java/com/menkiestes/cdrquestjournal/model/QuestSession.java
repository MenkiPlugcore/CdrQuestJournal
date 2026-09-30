package com.menkiestes.cdrquestjournal.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestSession {
    private final UUID playerId;
    private final String questId;
    private final long acceptedAt;
    private final long expiresAt;
    private final String cycleKey;
    private long lifecycleEndsAt;
    private final Map<String, Integer> progress;
    private QuestStatus status;

    public QuestSession(
            UUID playerId,
            String questId,
            long acceptedAt,
            long expiresAt,
            long lifecycleEndsAt,
            String cycleKey,
            QuestStatus status,
            Map<String, Integer> progress
    ) {
        this.playerId = playerId;
        this.questId = questId;
        this.acceptedAt = acceptedAt;
        this.expiresAt = Math.max(0L, expiresAt);
        this.lifecycleEndsAt = Math.max(0L, lifecycleEndsAt);
        this.cycleKey = cycleKey == null ? "" : cycleKey;
        this.status = status == null ? QuestStatus.ACTIVE : status;
        this.progress = new LinkedHashMap<>(progress == null ? Map.of() : progress);
    }

    public UUID playerId() { return playerId; }
    public String questId() { return questId; }
    public long acceptedAt() { return acceptedAt; }
    public long expiresAt() { return expiresAt; }
    public long lifecycleEndsAt() { return lifecycleEndsAt; }
    public void lifecycleEndsAt(long value) { this.lifecycleEndsAt = Math.max(0L, value); }
    public String cycleKey() { return cycleKey; }
    public QuestStatus status() { return status; }
    public void status(QuestStatus status) { this.status = status; }
    public Map<String, Integer> progress() { return progress; }
    public int progress(String objectiveId) { return progress.getOrDefault(objectiveId, 0); }
    public void progress(String objectiveId, int value) { progress.put(objectiveId, Math.max(0, value)); }

    public long effectiveExpiresAt() {
        if (expiresAt <= 0L) return lifecycleEndsAt;
        if (lifecycleEndsAt <= 0L) return expiresAt;
        return Math.min(expiresAt, lifecycleEndsAt);
    }

    public boolean hasDeadline() { return effectiveExpiresAt() > 0L; }

    public long remainingSeconds(long nowEpochSecond) {
        long deadline = effectiveExpiresAt();
        if (deadline <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, deadline - nowEpochSecond);
    }
}
