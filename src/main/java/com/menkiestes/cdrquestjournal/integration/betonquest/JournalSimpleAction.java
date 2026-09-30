package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.model.StartResult;
import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class JournalSimpleAction implements PlayerAction {
    public enum Mode { START, TURN_IN, FAIL, EXPIRE }

    private final JournalService service;
    private final NpcBindingService bindings;
    private final Mode mode;
    private final Argument<String> questId;

    public JournalSimpleAction(JournalService service, NpcBindingService bindings, Mode mode, Argument<String> questId) {
        this.service = service;
        this.bindings = bindings;
        this.mode = mode;
        this.questId = questId;
    }

    @Override
    public void execute(Profile profile) throws QuestException {
        Player player = Bukkit.getPlayer(profile.getPlayerUUID());
        if (player == null) throw new QuestException("CdrQuestJournal action requires an online player.");
        String id = questId.getValue(profile);

        switch (mode) {
            case START -> {
                NpcBindingService.Validation validation = bindings.validateStart(player, id);
                if (validation != NpcBindingService.Validation.OK) {
                    bindings.notifyValidation(player, id, validation);
                    throw new QuestException("Quest '" + id + "' must be started from its bound Citizens NPC.");
                }
                StartResult result = service.start(player, id);
                if (result != StartResult.STARTED && result != StartResult.ALREADY_ACTIVE) {
                    throw new QuestException("Could not start journal '" + id + "': " + result);
                }
            }
            case TURN_IN -> {
                NpcBindingService.Validation validation = bindings.validateTurnIn(player, id);
                if (validation != NpcBindingService.Validation.OK) {
                    bindings.notifyValidation(player, id, validation);
                    throw new QuestException("Journal '" + id + "' must be turned in to its bound Citizens NPC.");
                }
                if (!service.turnIn(player, id)) throw new QuestException("Journal '" + id + "' is not ready for turn-in.");
                bindings.clearContext(player);
            }
            case FAIL -> {
                if (!service.fail(player, id, false)) throw new QuestException("No active journal found for '" + id + "'.");
            }
            case EXPIRE -> {
                if (!service.fail(player, id, true)) throw new QuestException("No active journal found for '" + id + "'.");
            }
        }
    }

    @Override public boolean isPrimaryThreadEnforced() { return true; }
}
