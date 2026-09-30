package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.TurnInCoordinator;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class JournalTurnInTransactionAction implements PlayerAction {
    public enum Mode { PREPARE, FINALIZE, ABORT }

    private final TurnInCoordinator coordinator;
    private final Mode mode;
    private final Argument<String> questId;

    public JournalTurnInTransactionAction(TurnInCoordinator coordinator, Mode mode, Argument<String> questId) {
        this.coordinator = coordinator;
        this.mode = mode;
        this.questId = questId;
    }

    @Override
    public void execute(Profile profile) throws QuestException {
        Player player = Bukkit.getPlayer(profile.getPlayerUUID());
        if (player == null) throw new QuestException("CdrQuestJournal turn-in transaction requires an online player.");
        String id = questId.getValue(profile);
        boolean ok = switch (mode) {
            case PREPARE -> coordinator.prepare(player, id);
            case FINALIZE -> coordinator.finalizeReward(player, id);
            case ABORT -> coordinator.abort(player, id);
        };
        if (!ok) throw new QuestException("Turn-in transaction " + mode + " failed for '" + id + "'.");
    }

    @Override public boolean isPrimaryThreadEnforced() { return true; }
}
