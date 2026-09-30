package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.QuestAvailabilityService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;

public final class JournalHistoryCondition implements PlayerCondition {
    public enum Mode { COMPLETED, COOLDOWN_READY }

    private final QuestAvailabilityService availability;
    private final Mode mode;
    private final Argument<String> questId;

    public JournalHistoryCondition(QuestAvailabilityService availability, Mode mode, Argument<String> questId) {
        this.availability = availability;
        this.mode = mode;
        this.questId = questId;
    }

    @Override
    public boolean check(Profile profile) throws QuestException {
        String id = questId.getValue(profile);
        return switch (mode) {
            case COMPLETED -> availability.historyCompleted(profile.getPlayerUUID(), id);
            case COOLDOWN_READY -> availability.cooldownReady(profile.getPlayerUUID(), id);
        };
    }
}
