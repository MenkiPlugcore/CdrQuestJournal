package com.menkiestes.cdrquestjournal.model;

public record LimitedQuestWindow(long startAt, long endAt) {
    public LimitedQuestWindow {
        if (startAt <= 0) throw new IllegalArgumentException("startAt must be > 0");
        if (endAt <= startAt) throw new IllegalArgumentException("endAt must be greater than startAt");
    }

    public String cycleKey() {
        return "LIMITED:" + startAt;
    }
}
