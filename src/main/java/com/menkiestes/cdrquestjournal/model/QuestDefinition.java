package com.menkiestes.cdrquestjournal.model;

import java.util.List;
import java.util.Map;

public record QuestDefinition(
        String id,
        String title,
        String type,
        String giver,
        long timeLimitSeconds,
        List<String> description,
        Map<String, ObjectiveDefinition> objectives,
        List<String> rewards,
        List<String> failure,
        List<String> expiration
) {
    public QuestDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("quest id cannot be blank");
        if (title == null || title.isBlank()) title = id;
        if (type == null || type.isBlank()) type = "STORY";
        if (giver == null || giver.isBlank()) giver = "Unknown";
        timeLimitSeconds = Math.max(0L, timeLimitSeconds);
        description = description == null ? List.of() : List.copyOf(description);
        objectives = objectives == null ? Map.of() : Map.copyOf(objectives);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        failure = failure == null ? List.of() : List.copyOf(failure);
        expiration = expiration == null ? List.of() : List.copyOf(expiration);
    }
}
