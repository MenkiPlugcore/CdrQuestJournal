package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;

public final class JournalAvailabilityCondition implements PlayerCondition {
    private final JournalService service;
    private final Argument<String> questId;

    public JournalAvailabilityCondition(JournalService service, Argument<String> questId) {
        this.service = service;
        this.questId = questId;
    }

    @Override
    public boolean check(Profile profile) throws QuestException {
        return service.isAvailable(profile.getPlayerUUID(), questId.getValue(profile));
    }
}
