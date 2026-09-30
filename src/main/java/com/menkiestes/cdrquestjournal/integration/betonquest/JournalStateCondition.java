package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;

public final class JournalStateCondition implements PlayerCondition {
    public enum Mode { ACTIVE, READY, EXPIRED, ABANDONABLE }
    private final JournalService service;
    private final Mode mode;
    private final Argument<String> questId;
    public JournalStateCondition(JournalService service, Mode mode, Argument<String> questId) { this.service = service; this.mode = mode; this.questId = questId; }
    @Override public boolean check(Profile profile) throws QuestException {
        String id = questId.getValue(profile);
        return switch (mode) {
            case ACTIVE -> service.isActive(profile.getPlayerUUID(), id);
            case READY -> service.isReady(profile.getPlayerUUID(), id);
            case EXPIRED -> service.isExpired(profile.getPlayerUUID(), id);
            case ABANDONABLE -> service.canAbandon(profile.getPlayerUUID(), id);
        };
    }
    @Override public boolean isPrimaryThreadEnforced() { return true; }
}
