package com.menkiestes.cdrquestjournal.model;

import java.util.UUID;

public record QuestHistoryEntry(
        UUID playerId,
        String questId,
        QuestType questType,
        long startedAt,
        long endedAt,
        TerminalOutcome outcome,
        String giver,
        String cycleKey
) {
    public QuestHistoryEntry {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("questId cannot be blank");
        if (questType == null) questType = QuestType.STORY;
        if (outcome == null) throw new IllegalArgumentException("outcome cannot be null");
        giver = giver == null || giver.isBlank() ? "Unknown" : giver;
        cycleKey = cycleKey == null ? "" : cycleKey;
        startedAt = Math.max(0L, startedAt);
        endedAt = Math.max(startedAt, endedAt);
    }
}
