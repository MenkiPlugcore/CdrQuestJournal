package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;

public final class JournalSimpleActionFactory implements PlayerActionFactory {
    private final JournalService service;
    private final NpcBindingService bindings;
    private final JournalSimpleAction.Mode mode;

    public JournalSimpleActionFactory(JournalService service, NpcBindingService bindings, JournalSimpleAction.Mode mode) {
        this.service = service;
        this.bindings = bindings;
        this.mode = mode;
    }

    @Override
    public PlayerAction parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new JournalSimpleAction(service, bindings, mode, questId);
    }
}
