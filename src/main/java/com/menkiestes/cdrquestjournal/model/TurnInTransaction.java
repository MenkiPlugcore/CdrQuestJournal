package com.menkiestes.cdrquestjournal.model;

import java.util.UUID;

public record TurnInTransaction(
        UUID transactionId,
        UUID playerId,
        String questId,
        String cycleKey,
        long preparedAt,
        long rewardedAt,
        TurnInState state,
        int npcId,
        String npcName
) {
    public TurnInTransaction {
        if (transactionId == null) transactionId = UUID.randomUUID();
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("questId cannot be blank");
        if (cycleKey == null) cycleKey = "";
        if (preparedAt < 0L) preparedAt = 0L;
        if (rewardedAt < 0L) rewardedAt = 0L;
        if (state == null) state = TurnInState.PREPARED;
        if (npcName == null || npcName.isBlank()) npcName = "Quest Giver";
    }

    public TurnInTransaction markRewarded(long at) {
        return new TurnInTransaction(transactionId, playerId, questId, cycleKey, preparedAt,
                Math.max(at, preparedAt), TurnInState.REWARDED, npcId, npcName);
    }
}
