package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.QuestAvailabilityService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;

public final class StoryCompletedCondition implements PlayerCondition {
    private final QuestAvailabilityService availability;
    private final Argument<String> questId;

    public StoryCompletedCondition(QuestAvailabilityService availability, Argument<String> questId) {
        this.availability = availability;
        this.questId = questId;
    }

    @Override
    public boolean check(Profile profile) throws QuestException {
        return availability.isStoryCompleted(profile.getPlayerUUID(), questId.getValue(profile));
    }
}
