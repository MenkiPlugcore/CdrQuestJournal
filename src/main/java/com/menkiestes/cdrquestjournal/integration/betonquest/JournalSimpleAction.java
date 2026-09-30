package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.model.StartResult;
import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class JournalSimpleAction implements PlayerAction {
    public enum Mode { START, TURN_IN, FAIL, EXPIRE }
    private final JournalService service;
    private final Mode mode;
    private final Argument<String> questId;
    public JournalSimpleAction(JournalService service, Mode mode, Argument<String> questId) { this.service = service; this.mode = mode; this.questId = questId; }

    @Override public void execute(Profile profile) throws QuestException {
        Player player = Bukkit.getPlayer(profile.getPlayerUUID());
        if (player == null) throw new QuestException("CdrQuestJournal action requires an online player.");
        String id = questId.getValue(profile);
        switch (mode) {
            case START -> {
                StartResult result = service.start(player, id);
                if (result != StartResult.STARTED && result != StartResult.ALREADY_ACTIVE) throw new QuestException("Could not start journal '" + id + "': " + result);
            }
            case TURN_IN -> { if (!service.turnIn(player, id)) throw new QuestException("Journal '" + id + "' is not ready for turn-in."); }
            case FAIL -> { if (!service.fail(player, id, false)) throw new QuestException("No active journal found for '" + id + "'."); }
            case EXPIRE -> { if (!service.fail(player, id, true)) throw new QuestException("No active journal found for '" + id + "'."); }
        }
    }

    @Override public boolean isPrimaryThreadEnforced() { return true; }
}
