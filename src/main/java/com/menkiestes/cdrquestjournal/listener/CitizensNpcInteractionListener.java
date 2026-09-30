package com.menkiestes.cdrquestjournal.listener;

import com.menkiestes.cdrquestjournal.service.AdminGuiService;
import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class CitizensNpcInteractionListener implements Listener {
    private final NpcBindingService bindings;
    private final AdminGuiService adminGui;

    public CitizensNpcInteractionListener(NpcBindingService bindings, AdminGuiService adminGui) {
        this.bindings = bindings;
        this.adminGui = adminGui;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcRightClick(NPCRightClickEvent event) {
        bindings.recordClick(event.getClicker(), event.getNPC());
        adminGui.handleNpcSelection(event.getClicker(), event.getNPC());
    }
}
