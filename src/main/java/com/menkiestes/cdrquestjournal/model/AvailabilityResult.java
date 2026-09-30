package com.menkiestes.cdrquestjournal.model;

import java.util.Map;

public record AvailabilityResult(
        boolean available,
        String messageKey,
        String cycleKey,
        long cycleEndsAt,
        Map<String, String> placeholders
) {
    public AvailabilityResult {
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        cycleKey = cycleKey == null ? "" : cycleKey;
        messageKey = messageKey == null ? "" : messageKey;
    }

    public static AvailabilityResult available(String cycleKey, long cycleEndsAt) {
        return new AvailabilityResult(true, "", cycleKey, cycleEndsAt, Map.of());
    }

    public static AvailabilityResult denied(String messageKey, String cycleKey, long cycleEndsAt, Map<String, String> placeholders) {
        return new AvailabilityResult(false, messageKey, cycleKey, cycleEndsAt, placeholders);
    }
}
