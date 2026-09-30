package com.menkiestes.cdrquestjournal.model;

import java.util.List;
import java.util.Map;

public record QuestDefinition(
        String id,
        String title,
        QuestType type,
        String giver,
        long timeLimitSeconds,
        List<String> description,
        Map<String, ObjectiveDefinition> objectives,
        List<String> rewards,
        List<String> failure,
        List<String> expiration,
        boolean storyRepeatable,
        List<String> requiresAll,
        List<String> requiresAny,
        boolean abandonAllowed,
        long cooldownSeconds
) {
    public QuestDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("quest id cannot be blank");
        if (title == null || title.isBlank()) title = id;
        if (type == null) type = QuestType.STORY;
        if (giver == null || giver.isBlank()) giver = "Unknown";
        timeLimitSeconds = Math.max(0L, timeLimitSeconds);
        description = description == null ? List.of() : List.copyOf(description);
        objectives = objectives == null ? Map.of() : Map.copyOf(objectives);
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        failure = failure == null ? List.of() : List.copyOf(failure);
        expiration = expiration == null ? List.of() : List.copyOf(expiration);
        requiresAll = normalize(requiresAll);
        requiresAny = normalize(requiresAny);
        cooldownSeconds = Math.max(0L, cooldownSeconds);
    }

    private static List<String> normalize(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream().filter(id -> id != null && !id.isBlank()).map(String::toLowerCase).distinct().toList();
    }
}
