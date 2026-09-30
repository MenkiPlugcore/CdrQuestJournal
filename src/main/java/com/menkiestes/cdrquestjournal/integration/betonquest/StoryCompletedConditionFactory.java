package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.QuestAvailabilityService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;

public final class StoryCompletedConditionFactory implements PlayerConditionFactory {
    private final QuestAvailabilityService availability;

    public StoryCompletedConditionFactory(QuestAvailabilityService availability) {
        this.availability = availability;
    }

    @Override
    public PlayerCondition parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new StoryCompletedCondition(availability, questId);
    }
}
