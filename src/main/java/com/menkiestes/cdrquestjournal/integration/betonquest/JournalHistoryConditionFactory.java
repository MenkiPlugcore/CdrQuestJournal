package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.QuestAvailabilityService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;

public final class JournalHistoryConditionFactory implements PlayerConditionFactory {
    private final QuestAvailabilityService availability;
    private final JournalHistoryCondition.Mode mode;

    public JournalHistoryConditionFactory(QuestAvailabilityService availability, JournalHistoryCondition.Mode mode) {
        this.availability = availability;
        this.mode = mode;
    }

    @Override
    public PlayerCondition parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new JournalHistoryCondition(availability, mode, questId);
    }
}
