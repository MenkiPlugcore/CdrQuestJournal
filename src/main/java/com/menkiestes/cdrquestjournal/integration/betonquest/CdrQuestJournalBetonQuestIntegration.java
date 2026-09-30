package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import org.betonquest.betonquest.api.BetonQuestApi;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.integration.Integration;

public final class CdrQuestJournalBetonQuestIntegration implements Integration {
    private final JournalService service;
    private final NpcBindingService bindings;

    public CdrQuestJournalBetonQuestIntegration(JournalService service, NpcBindingService bindings) {
        this.service = service;
        this.bindings = bindings;
    }

    @Override
    public void enable(BetonQuestApi api) throws QuestException {
        api.actions().registry().register("cdrjournal_start", new JournalSimpleActionFactory(service, bindings, JournalSimpleAction.Mode.START));
        api.actions().registry().register("cdrjournal_turnin", new JournalSimpleActionFactory(service, bindings, JournalSimpleAction.Mode.TURN_IN));
        api.actions().registry().register("cdrjournal_fail", new JournalSimpleActionFactory(service, bindings, JournalSimpleAction.Mode.FAIL));
        api.actions().registry().register("cdrjournal_expire", new JournalSimpleActionFactory(service, bindings, JournalSimpleAction.Mode.EXPIRE));
        api.actions().registry().register("cdrjournal_progress", new JournalProgressActionFactory(service));

        api.conditions().registry().register("cdrjournal_active", new JournalStateConditionFactory(service, JournalStateCondition.Mode.ACTIVE));
        api.conditions().registry().register("cdrjournal_ready", new JournalStateConditionFactory(service, JournalStateCondition.Mode.READY));
        api.conditions().registry().register("cdrjournal_expired", new JournalStateConditionFactory(service, JournalStateCondition.Mode.EXPIRED));
        api.conditions().registry().register("cdrjournal_available", new JournalAvailabilityConditionFactory(service));
        api.conditions().registry().register("cdrjournal_correct_npc", new JournalNpcConditionFactory(bindings));
    }

    @Override public void postEnable(BetonQuestApi api) throws QuestException {}
    @Override public void disable() throws QuestException {}
}
