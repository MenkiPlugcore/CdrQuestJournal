package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;

public final class JournalSimpleActionFactory implements PlayerActionFactory {
    private final JournalService service;
    private final JournalSimpleAction.Mode mode;
    public JournalSimpleActionFactory(JournalService service, JournalSimpleAction.Mode mode) { this.service = service; this.mode = mode; }
    @Override public PlayerAction parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new JournalSimpleAction(service, mode, questId);
    }
}
