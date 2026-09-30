package com.menkiestes.cdrquestjournal.listener;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.TurnInCoordinator;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class JournalProtectionListener implements Listener {
    private final CdrQuestJournalPlugin plugin;
    private final JournalService service;
    private final TurnInCoordinator turnIns;

    public JournalProtectionListener(CdrQuestJournalPlugin plugin, JournalService service, TurnInCoordinator turnIns) {
        this.plugin = plugin;
        this.service = service;
        this.turnIns = turnIns;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (protectionEnabled() && service.isJournalOwnedBy(event.getItemDrop().getItemStack(), event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!protectionEnabled() || !(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        boolean externalInventory = event.getView().getTopInventory().getType() != InventoryType.CRAFTING && event.getView().getTopInventory().getType() != InventoryType.PLAYER;
        if (clickedTop && service.isJournalOwnedBy(cursor, player.getUniqueId())) { event.setCancelled(true); return; }
        if (externalInventory && event.isShiftClick() && service.isJournalOwnedBy(current, player.getUniqueId())) { event.setCancelled(true); return; }
        int hotbar = event.getHotbarButton();
        if (clickedTop && hotbar >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(hotbar);
            if (service.isJournalOwnedBy(hotbarItem, player.getUniqueId())) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!protectionEnabled() || !(event.getWhoClicked() instanceof Player player)) return;
        if (!service.isJournalOwnedBy(event.getOldCursor(), player.getUniqueId())) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (!protectionEnabled() || !service.isJournalOwnedBy(event.getItem(), event.getPlayer().getUniqueId()) || event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        if (type == Material.LECTERN || type == Material.CHISELED_BOOKSHELF) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!protectionEnabled() || !(event.getRightClicked() instanceof ItemFrame)) return;
        ItemStack hand = event.getHand() == EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (service.isJournalOwnedBy(hand, event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        service.removeJournalFromDrops(event.getDrops(), event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> recoverAndRestore(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("journal.restore-on-join", true)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> recoverAndRestore(event.getPlayer()));
        }
    }

    private void recoverAndRestore(Player player) {
        turnIns.recoverPlayer(player);
        service.restorePlayer(player);
    }

    private boolean protectionEnabled() {
        return plugin.getConfig().getBoolean("journal.protect-item", true);
    }
}
