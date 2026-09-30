package com.menkiestes.cdrquestjournal.listener;

import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class CitizensNpcInteractionListener implements Listener {
    private final NpcBindingService bindings;

    public CitizensNpcInteractionListener(NpcBindingService bindings) {
        this.bindings = bindings;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcRightClick(NPCRightClickEvent event) {
        bindings.recordClick(event.getClicker(), event.getNPC());
    }
}
