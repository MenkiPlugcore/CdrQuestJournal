package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class JournalNpcCondition implements PlayerCondition {
    private final NpcBindingService bindings;
    private final Argument<String> questId;

    public JournalNpcCondition(NpcBindingService bindings, Argument<String> questId) {
        this.bindings = bindings;
        this.questId = questId;
    }

    @Override
    public boolean check(Profile profile) throws QuestException {
        Player player = Bukkit.getPlayer(profile.getPlayerUUID());
        if (player == null) return false;
        return bindings.validateTurnIn(player, questId.getValue(profile)) == NpcBindingService.Validation.OK;
    }

    @Override
    public boolean isPrimaryThreadEnforced() { return true; }
}
