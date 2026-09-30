package com.menkiestes.cdrquestjournal.model;

public record ObjectiveDefinition(String id, String text, int target) {
    public ObjectiveDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("objective id cannot be blank");
        if (text == null || text.isBlank()) text = id;
        if (target < 1) target = 1;
    }
}
