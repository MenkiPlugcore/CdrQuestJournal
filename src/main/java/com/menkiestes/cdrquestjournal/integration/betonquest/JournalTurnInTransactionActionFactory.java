package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.TurnInCoordinator;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;

public final class JournalTurnInTransactionActionFactory implements PlayerActionFactory {
    private final TurnInCoordinator coordinator;
    private final JournalTurnInTransactionAction.Mode mode;

    public JournalTurnInTransactionActionFactory(TurnInCoordinator coordinator, JournalTurnInTransactionAction.Mode mode) {
        this.coordinator = coordinator;
        this.mode = mode;
    }

    @Override
    public PlayerAction parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new JournalTurnInTransactionAction(coordinator, mode, questId);
    }
}
