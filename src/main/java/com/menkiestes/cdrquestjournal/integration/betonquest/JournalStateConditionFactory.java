package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;

public final class JournalStateConditionFactory implements PlayerConditionFactory {
    private final JournalService service;
    private final JournalStateCondition.Mode mode;
    public JournalStateConditionFactory(JournalService service, JournalStateCondition.Mode mode) { this.service = service; this.mode = mode; }
    @Override public PlayerCondition parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new JournalStateCondition(service, mode, questId);
    }
}
