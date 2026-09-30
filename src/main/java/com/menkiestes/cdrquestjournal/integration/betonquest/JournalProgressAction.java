package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.model.ProgressMode;
import com.menkiestes.cdrquestjournal.service.JournalService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class JournalProgressAction implements PlayerAction {
    private final JournalService service;
    private final Argument<String> questId;
    private final Argument<String> objectiveId;
    private final Argument<String> mode;
    private final Argument<Number> amount;
    public JournalProgressAction(JournalService service, Argument<String> questId, Argument<String> objectiveId, Argument<String> mode, Argument<Number> amount) {
        this.service = service; this.questId = questId; this.objectiveId = objectiveId; this.mode = mode; this.amount = amount;
    }
    @Override public void execute(Profile profile) throws QuestException {
        Player player = Bukkit.getPlayer(profile.getPlayerUUID());
        if (player == null) throw new QuestException("CdrQuestJournal progress action requires an online player.");
        String quest = questId.getValue(profile);
        String objective = objectiveId.getValue(profile);
        ProgressMode progressMode;
        try { progressMode = ProgressMode.parse(mode.getValue(profile)); }
        catch (IllegalArgumentException ex) { throw new QuestException(ex.getMessage()); }
        int value = amount.getValue(profile).intValue();
        if (!service.updateProgress(player, quest, objective, progressMode, value)) throw new QuestException("Could not update journal progress for " + quest + "/" + objective);
    }
    @Override public boolean isPrimaryThreadEnforced() { return true; }
}
