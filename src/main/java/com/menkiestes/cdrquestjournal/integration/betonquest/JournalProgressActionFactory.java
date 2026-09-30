package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;

public final class JournalProgressActionFactory implements PlayerActionFactory {
    private final JournalService service;
    public JournalProgressActionFactory(JournalService service) { this.service = service; }
    @Override public PlayerAction parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        Argument<String> objectiveId = instruction.string().get();
        Argument<String> mode = instruction.string().get();
        Argument<Number> amount = instruction.number().get();
        return new JournalProgressAction(service, questId, objectiveId, mode, amount);
    }
}
