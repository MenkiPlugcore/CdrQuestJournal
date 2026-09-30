package com.menkiestes.cdrquestjournal.model;

public enum ProgressMode {
    ADD,
    SET;

    public static ProgressMode parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("Progress mode cannot be null");
        return switch (raw.trim().toLowerCase()) {
            case "add", "+" -> ADD;
            case "set", "=" -> SET;
            default -> throw new IllegalArgumentException("Unknown progress mode: " + raw);
        };
    }
}
