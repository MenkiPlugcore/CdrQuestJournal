package com.menkiestes.cdrquestjournal.integration.betonquest;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import com.menkiestes.cdrquestjournal.service.TurnInCoordinator;
import org.betonquest.betonquest.api.integration.IntegrationService;

public final class BetonQuestBootstrap {
    private BetonQuestBootstrap() {}

    public static boolean register(CdrQuestJournalPlugin plugin, JournalService service,
                                   NpcBindingService bindings, TurnInCoordinator turnIns) {
        IntegrationService integrationService = plugin.getServer().getServicesManager().load(IntegrationService.class);
        if (integrationService == null) {
            plugin.getLogger().warning("BetonQuest terdeteksi tetapi IntegrationService belum tersedia. Hook dilewati.");
            return false;
        }
        integrationService.withPolicies().register(plugin, () -> new CdrQuestJournalBetonQuestIntegration(service, bindings, turnIns));
        return true;
    }
}
