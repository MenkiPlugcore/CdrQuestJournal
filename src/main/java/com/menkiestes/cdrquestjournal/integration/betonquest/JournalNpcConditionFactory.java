package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;

public final class JournalNpcConditionFactory implements PlayerConditionFactory {
    private final NpcBindingService bindings;

    public JournalNpcConditionFactory(NpcBindingService bindings) {
        this.bindings = bindings;
    }

    @Override
    public PlayerCondition parsePlayer(Instruction instruction) throws QuestException {
        Argument<String> questId = instruction.string().get();
        return new JournalNpcCondition(bindings, questId);
    }
}
