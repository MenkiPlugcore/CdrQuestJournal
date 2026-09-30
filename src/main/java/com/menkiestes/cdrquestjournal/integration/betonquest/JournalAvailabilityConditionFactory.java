package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;

public final class JournalAvailabilityConditionFactory implements PlayerConditionFactory {
    private final JournalService service;

    public JournalAvailabilityConditionFactory(JournalService service) {
        this.service = service;
    }

    @Override
    public PlayerCondition parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new JournalAvailabilityCondition(service, questId);
    }
}
