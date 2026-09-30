package com.menkiestes.cdrquestjournal.model;

public enum QuestType {
    STORY,
    DAILY,
    LIMITED;

    public static QuestType from(String raw) {
        if (raw == null || raw.isBlank()) return STORY;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return STORY;
        }
    }
}
