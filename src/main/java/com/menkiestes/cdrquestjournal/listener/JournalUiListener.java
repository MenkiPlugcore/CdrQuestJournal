package com.menkiestes.cdrquestjournal.listener;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.service.JournalUiService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class JournalUiListener implements Listener {
    private final CdrQuestJournalPlugin plugin;
    private final JournalUiService ui;

    public JournalUiListener(CdrQuestJournalPlugin plugin, JournalUiService ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ui.refreshHand(event.getPlayer(), event.getHand());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> ui.refreshAll(event.getPlayer()));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> ui.refreshAll(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> ui.refreshAll(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> ui.refreshAll(event.getPlayer()), 2L);
    }
}
