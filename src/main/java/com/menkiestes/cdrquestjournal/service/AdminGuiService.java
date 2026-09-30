package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.LimitedQuestWindow;
import com.menkiestes.cdrquestjournal.model.NpcBinding;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestHistoryEntry;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.model.QuestType;
import com.menkiestes.cdrquestjournal.model.TerminalOutcome;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class AdminGuiService implements Listener {
    private static final int PAGE_SIZE = 45;

    private enum View {
        DASHBOARD, QUESTS, LIMITED, QUEST_DETAIL, PLAYERS, PLAYER_DETAIL, PLAYER_HISTORY, PLAYER_COOLDOWNS
    }

    private record GuiHolder(View view, int page, String target) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record CooldownRow(QuestDefinition definition, long remainingSeconds) {}

    private final CdrQuestJournalPlugin plugin;
    private final JournalService journals;
    private final QuestAvailabilityService availability;
    private final NpcBindingService bindings;
    private final Map<UUID, String> pendingNpcBindings = new ConcurrentHashMap<>();

    public AdminGuiService(CdrQuestJournalPlugin plugin, JournalService journals,
                           QuestAvailabilityService availability, NpcBindingService bindings) {
        this.plugin = plugin;
        this.journals = journals;
        this.availability = availability;
        this.bindings = bindings;
    }

    public void openDashboard(Player player) {
        Inventory inventory = Bukkit.createInventory(new GuiHolder(View.DASHBOARD, 0, ""), 27, color("&8CdrQuestJournal &7• &6Admin"));
        fill(inventory);
        inventory.setItem(10, item(Material.WRITTEN_BOOK, "&6&lQuest Manager",
                "&7Browse semua quest.", "&7Klik untuk membuka."));
        inventory.setItem(12, item(Material.CLOCK, "&d&lLimited Quest Manager",
                "&7Schedule, start, extend,", "&7atau clear limited quest."));
        inventory.setItem(14, item(Material.PLAYER_HEAD, "&b&lPlayer Sessions",
                "&7Inspect session, history,", "&7cooldown, dan story player."));
        inventory.setItem(16, item(Material.REDSTONE, "&a&lReload",
                "&7Reload config, quests,", "&7lifecycle dan binding."));
        player.openInventory(inventory);
    }

    private void openQuestList(Player player, int requestedPage, boolean limitedOnly) {
        List<QuestDefinition> definitions = plugin.getQuestRegistry().all().stream()
                .filter(def -> !limitedOnly || def.type() == QuestType.LIMITED)
                .sorted(Comparator.comparing(QuestDefinition::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int maxPage = Math.max(0, (definitions.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        View view = limitedOnly ? View.LIMITED : View.QUESTS;
        String title = limitedOnly ? "&8Limited Quests &7• &f" + (page + 1) : "&8All Quests &7• &f" + (page + 1);
        Inventory inventory = Bukkit.createInventory(new GuiHolder(view, page, ""), 54, color(title));
        fill(inventory);

        int from = page * PAGE_SIZE;
        int to = Math.min(definitions.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            QuestDefinition def = definitions.get(i);
            inventory.setItem(i - from, questIcon(def));
        }

        inventory.setItem(45, item(Material.ARROW, "&eBack", "&7Kembali ke dashboard."));
        if (page > 0) inventory.setItem(48, item(Material.ARROW, "&ePrevious Page", "&7Page " + page));
        inventory.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + "/" + (maxPage + 1), "&7Total: &f" + definitions.size()));
        if (page < maxPage) inventory.setItem(50, item(Material.ARROW, "&eNext Page", "&7Page " + (page + 2)));
        player.openInventory(inventory);
    }

    private void openQuestDetail(Player player, String questId) {
        QuestDefinition def = plugin.getQuestRegistry().get(questId).orElse(null);
        if (def == null) {
            player.sendMessage(color("&cQuest tidak ditemukan."));
            openDashboard(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new GuiHolder(View.QUEST_DETAIL, 0, def.id()), 45,
                color("&8Quest &7• &f" + trim(def.title(), 23)));
        fill(inventory);
        inventory.setItem(4, questIcon(def));

        NpcBinding binding = bindings.binding(def.id()).orElse(null);
        List<String> npcLore = new ArrayList<>();
        if (binding == null) {
            npcLore.add("&cBelum terikat ke NPC.");
        } else {
            npcLore.add("&7NPC: &f" + binding.npcName());
            npcLore.add("&7Citizens ID: &f" + binding.npcId());
            npcLore.add("&7Resolved: " + (bindings.resolveNpc(binding).isPresent() ? "&aYES" : "&cNO"));
        }
        npcLore.add("");
        npcLore.add("&eKlik untuk memilih NPC baru.");
        inventory.setItem(20, item(Material.VILLAGER_SPAWN_EGG, "&6&lQuest Giver NPC", npcLore.toArray(String[]::new)));
        if (binding != null) inventory.setItem(29, item(Material.BARRIER, "&cUnbind NPC", "&7Hapus binding NPC quest ini."));

        if (def.type() == QuestType.LIMITED) {
            LimitedQuestWindow window = availability.limitedWindow(def.id()).orElse(null);
            inventory.setItem(22, limitedStatusItem(def, window));
            inventory.setItem(31, item(Material.LIME_DYE, "&aStart 1 Hour", "&7Mulai event sekarang selama 1 jam."));
            inventory.setItem(32, item(Material.LIME_DYE, "&aStart 6 Hours", "&7Mulai event sekarang selama 6 jam."));
            inventory.setItem(33, item(Material.LIME_DYE, "&aStart 24 Hours", "&7Mulai event sekarang selama 24 jam."));
            inventory.setItem(38, item(Material.CLOCK, "&eExtend +1 Hour", "&7Tambah 1 jam dari end saat ini."));
            inventory.setItem(39, item(Material.CLOCK, "&eExtend +6 Hours", "&7Tambah 6 jam dari end saat ini."));
            inventory.setItem(40, item(Material.CLOCK, "&eExtend +24 Hours", "&7Tambah 24 jam dari end saat ini."));
            inventory.setItem(42, item(Material.REDSTONE_BLOCK, "&cClear Schedule", "&7Hapus schedule limited quest."));
        } else {
            inventory.setItem(22, item(Material.MAP, "&bLifecycle",
                    "&7Type: &f" + def.type().name(),
                    def.type() == QuestType.DAILY ? "&7Reset: &f" + availability.dailyReset() + " " + availability.zoneId().getId() : "&7Story quest tidak memiliki schedule global."));
        }

        inventory.setItem(24, item(Material.IRON_DOOR, "&e&lAbandon Policy",
                "&7Allowed: " + (def.abandonAllowed() ? "&aYES" : "&cNO"),
                "&7Abandon tetap harus melalui", "&7Quest Giver NPC."));
        inventory.setItem(26, item(Material.RECOVERY_COMPASS, "&b&lCooldown",
                "&7Duration: &f" + (def.cooldownSeconds() <= 0 ? "None" : formatRemaining(def.cooldownSeconds())),
                "&7Dimulai setelah outcome terminal."));

        inventory.setItem(36, item(Material.ARROW, "&eBack to Quests", "&7Kembali ke daftar quest."));
        inventory.setItem(44, item(Material.NETHER_STAR, "&6Admin Notes",
                "&7Exact limited date/time tetap bisa", "&7diatur lewat &f/cqj limited set&7."));
        player.openInventory(inventory);
    }

    private void openPlayers(Player player, int requestedPage) {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int maxPage = Math.max(0, (players.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inventory = Bukkit.createInventory(new GuiHolder(View.PLAYERS, page, ""), 54,
                color("&8Player Sessions &7• &f" + (page + 1)));
        fill(inventory);
        int from = page * PAGE_SIZE;
        int to = Math.min(players.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Player target = players.get(i);
            int count = journals.sessions(target.getUniqueId()).size();
            inventory.setItem(i - from, item(Material.PLAYER_HEAD, "&b" + target.getName(),
                    "&7Active journals: &f" + count,
                    "&7History entries: &f" + availability.historyCount(target.getUniqueId()),
                    "&eKlik untuk inspect."));
        }
        inventory.setItem(45, item(Material.ARROW, "&eBack", "&7Kembali ke dashboard."));
        if (page > 0) inventory.setItem(48, item(Material.ARROW, "&ePrevious Page"));
        inventory.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + "/" + (maxPage + 1), "&7Online: &f" + players.size()));
        if (page < maxPage) inventory.setItem(50, item(Material.ARROW, "&eNext Page"));
        player.openInventory(inventory);
    }

    private void openPlayerDetail(Player admin, Player target) {
        Inventory inventory = Bukkit.createInventory(new GuiHolder(View.PLAYER_DETAIL, 0, target.getUniqueId().toString()), 54,
                color("&8Sessions &7• &f" + trim(target.getName(), 24)));
        fill(inventory);
        List<QuestSession> sessions = new ArrayList<>(journals.sessions(target.getUniqueId()));
        sessions.sort(Comparator.comparing(QuestSession::questId, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < Math.min(45, sessions.size()); i++) {
            QuestSession session = sessions.get(i);
            QuestDefinition def = journals.definition(session.questId()).orElse(null);
            String title = def == null ? session.questId() : def.title();
            long remaining = journals.remainingSeconds(session);
            inventory.setItem(i, item(Material.BOOK, "&f" + title,
                    "&7Status: &f" + session.status().name(),
                    "&7Time: &f" + formatRemaining(remaining),
                    "&7Quest ID: &8" + session.questId()));
        }
        inventory.setItem(45, item(Material.ARROW, "&eBack to Players"));
        inventory.setItem(47, item(Material.WRITABLE_BOOK, "&6Quest History",
                "&7Entries: &f" + availability.historyCount(target.getUniqueId()),
                "&eKlik untuk membuka."));
        inventory.setItem(48, item(Material.RECOVERY_COMPASS, "&bActive Cooldowns", "&eKlik untuk inspect."));
        inventory.setItem(49, item(Material.CHEST, "&aRestore Journals", "&7Pulihkan seluruh journal aktif", "&7milik &f" + target.getName() + "&7."));
        admin.openInventory(inventory);
    }

    private void openPlayerHistory(Player admin, Player target, int requestedPage) {
        List<QuestHistoryEntry> entries = availability.history(target.getUniqueId());
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inventory = Bukkit.createInventory(new GuiHolder(View.PLAYER_HISTORY, page, target.getUniqueId().toString()), 54,
                color("&8Quest History &7• &f" + trim(target.getName(), 20)));
        fill(inventory);
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            QuestHistoryEntry entry = entries.get(i);
            QuestDefinition def = plugin.getQuestRegistry().get(entry.questId()).orElse(null);
            String title = def == null ? entry.questId() : def.title();
            inventory.setItem(i - from, item(historyMaterial(entry.outcome()), "&f" + title,
                    "&7Outcome: &f" + entry.outcome().name(),
                    "&7Type: &f" + entry.questType().name(),
                    "&7Started: &f" + availability.formatAt(entry.startedAt()),
                    "&7Ended: &f" + availability.formatAt(entry.endedAt()),
                    "&7Giver: &f" + entry.giver(),
                    "&7Quest ID: &8" + entry.questId()));
        }
        inventory.setItem(45, item(Material.ARROW, "&eBack to Player"));
        if (page > 0) inventory.setItem(48, item(Material.ARROW, "&ePrevious Page"));
        inventory.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + "/" + (maxPage + 1), "&7Entries: &f" + entries.size()));
        if (page < maxPage) inventory.setItem(50, item(Material.ARROW, "&eNext Page"));
        admin.openInventory(inventory);
    }

    private void openPlayerCooldowns(Player admin, Player target, int requestedPage) {
        List<CooldownRow> rows = plugin.getQuestRegistry().all().stream()
                .map(def -> new CooldownRow(def, availability.cooldownRemaining(target.getUniqueId(), def)))
                .filter(row -> row.remainingSeconds() > 0L)
                .sorted(Comparator.comparing(row -> row.definition().id(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        int maxPage = Math.max(0, (rows.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Inventory inventory = Bukkit.createInventory(new GuiHolder(View.PLAYER_COOLDOWNS, page, target.getUniqueId().toString()), 54,
                color("&8Cooldowns &7• &f" + trim(target.getName(), 22)));
        fill(inventory);
        int from = page * PAGE_SIZE;
        int to = Math.min(rows.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            CooldownRow row = rows.get(i);
            long readyAt = Instant.now().getEpochSecond() + row.remainingSeconds();
            inventory.setItem(i - from, item(Material.CLOCK, "&b" + row.definition().title(),
                    "&7Remaining: &f" + formatRemaining(row.remainingSeconds()),
                    "&7Ready: &f" + availability.formatAt(readyAt),
                    "&7Quest ID: &8" + row.definition().id()));
        }
        inventory.setItem(45, item(Material.ARROW, "&eBack to Player"));
        if (page > 0) inventory.setItem(48, item(Material.ARROW, "&ePrevious Page"));
        inventory.setItem(49, item(Material.PAPER, "&fPage " + (page + 1) + "/" + (maxPage + 1), "&7Active cooldowns: &f" + rows.size()));
        if (page < maxPage) inventory.setItem(50, item(Material.ARROW, "&eNext Page"));
        admin.openInventory(inventory);
    }

    public boolean handleNpcSelection(Player player, NPC npc) {
        String questId = pendingNpcBindings.remove(player.getUniqueId());
        if (questId == null) return false;
        QuestDefinition definition = plugin.getQuestRegistry().get(questId).orElse(null);
        if (definition == null) {
            player.sendMessage(color("&cQuest untuk NPC selection tidak lagi tersedia."));
            return true;
        }
        NpcBinding binding = bindings.bind(definition.id(), npc);
        player.sendMessage(color("&aQuest &f" + definition.title() + " &aberhasil di-bind ke &f" + binding.npcName() + " &8(ID " + binding.npcId() + ")&a."));
        plugin.getServer().getScheduler().runTask(plugin, () -> openQuestDetail(player, definition.id()));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("cdrquestjournal.admin")) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();

        switch (holder.view()) {
            case DASHBOARD -> handleDashboard(player, slot);
            case QUESTS -> handleQuestList(player, holder.page(), slot, false);
            case LIMITED -> handleQuestList(player, holder.page(), slot, true);
            case QUEST_DETAIL -> handleQuestDetail(player, holder.target(), slot);
            case PLAYERS -> handlePlayerList(player, holder.page(), slot);
            case PLAYER_DETAIL -> handlePlayerDetail(player, holder.target(), slot);
            case PLAYER_HISTORY -> handlePlayerHistory(player, holder.target(), holder.page(), slot);
            case PLAYER_COOLDOWNS -> handlePlayerCooldowns(player, holder.target(), holder.page(), slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) event.setCancelled(true);
    }

    private void handleDashboard(Player player, int slot) {
        switch (slot) {
            case 10 -> openQuestList(player, 0, false);
            case 12 -> openQuestList(player, 0, true);
            case 14 -> openPlayers(player, 0);
            case 16 -> {
                plugin.reloadAll();
                player.sendMessage(color("&aCdrQuestJournal berhasil direload."));
                openDashboard(player);
            }
            default -> { }
        }
    }

    private void handleQuestList(Player player, int page, int slot, boolean limitedOnly) {
        if (slot == 45) { openDashboard(player); return; }
        if (slot == 48 && page > 0) { openQuestList(player, page - 1, limitedOnly); return; }
        if (slot == 50) { openQuestList(player, page + 1, limitedOnly); return; }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        List<QuestDefinition> definitions = plugin.getQuestRegistry().all().stream()
                .filter(def -> !limitedOnly || def.type() == QuestType.LIMITED)
                .sorted(Comparator.comparing(QuestDefinition::id, String.CASE_INSENSITIVE_ORDER)).toList();
        int index = page * PAGE_SIZE + slot;
        if (index < definitions.size()) openQuestDetail(player, definitions.get(index).id());
    }

    private void handleQuestDetail(Player player, String questId, int slot) {
        QuestDefinition def = plugin.getQuestRegistry().get(questId).orElse(null);
        if (def == null) { openDashboard(player); return; }
        if (slot == 36) { openQuestList(player, 0, false); return; }
        if (slot == 20) {
            pendingNpcBindings.put(player.getUniqueId(), def.id());
            player.closeInventory();
            player.sendMessage(color("&eNPC selection aktif untuk &f" + def.title() + "&e. Klik kanan Citizens NPC yang ingin dijadikan Quest Giver."));
            return;
        }
        if (slot == 29 && bindings.binding(def.id()).isPresent()) {
            bindings.unbind(def.id());
            player.sendMessage(color("&aNPC binding untuk &f" + def.title() + " &atelah dihapus."));
            openQuestDetail(player, def.id());
            return;
        }
        if (def.type() != QuestType.LIMITED) return;
        switch (slot) {
            case 31 -> startLimited(player, def, 1);
            case 32 -> startLimited(player, def, 6);
            case 33 -> startLimited(player, def, 24);
            case 38 -> extendLimited(player, def, 1);
            case 39 -> extendLimited(player, def, 6);
            case 40 -> extendLimited(player, def, 24);
            case 42 -> clearLimited(player, def);
            default -> { }
        }
    }

    private void handlePlayerList(Player admin, int page, int slot) {
        if (slot == 45) { openDashboard(admin); return; }
        if (slot == 48 && page > 0) { openPlayers(admin, page - 1); return; }
        if (slot == 50) { openPlayers(admin, page + 1); return; }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int index = page * PAGE_SIZE + slot;
        if (index < players.size()) openPlayerDetail(admin, players.get(index));
    }

    private void handlePlayerDetail(Player admin, String uuidRaw, int slot) {
        Player target = onlinePlayer(uuidRaw, admin);
        if (target == null) return;
        if (slot == 45) { openPlayers(admin, 0); return; }
        if (slot == 47) { openPlayerHistory(admin, target, 0); return; }
        if (slot == 48) { openPlayerCooldowns(admin, target, 0); return; }
        if (slot == 49) {
            journals.restorePlayer(target);
            plugin.getJournalUiService().refreshAll(target);
            admin.sendMessage(color("&aJournal aktif milik &f" + target.getName() + " &atelah dipulihkan."));
            openPlayerDetail(admin, target);
        }
    }

    private void handlePlayerHistory(Player admin, String uuidRaw, int page, int slot) {
        Player target = onlinePlayer(uuidRaw, admin);
        if (target == null) return;
        if (slot == 45) { openPlayerDetail(admin, target); return; }
        if (slot == 48 && page > 0) { openPlayerHistory(admin, target, page - 1); return; }
        if (slot == 50) openPlayerHistory(admin, target, page + 1);
    }

    private void handlePlayerCooldowns(Player admin, String uuidRaw, int page, int slot) {
        Player target = onlinePlayer(uuidRaw, admin);
        if (target == null) return;
        if (slot == 45) { openPlayerDetail(admin, target); return; }
        if (slot == 48 && page > 0) { openPlayerCooldowns(admin, target, page - 1); return; }
        if (slot == 50) openPlayerCooldowns(admin, target, page + 1);
    }

    private Player onlinePlayer(String uuidRaw, Player admin) {
        UUID uuid;
        try { uuid = UUID.fromString(uuidRaw); }
        catch (IllegalArgumentException ex) { openPlayers(admin, 0); return null; }
        Player target = Bukkit.getPlayer(uuid);
        if (target == null) {
            admin.sendMessage(color("&cPlayer sudah offline."));
            openPlayers(admin, 0);
        }
        return target;
    }

    private void startLimited(Player player, QuestDefinition def, int hours) {
        long start = Instant.now().getEpochSecond();
        long end = start + hours * 3600L;
        availability.setLimitedWindow(def.id(), start, end);
        journals.refreshQuestForOnlinePlayers(def.id());
        player.sendMessage(color("&a" + def.title() + " dimulai selama &f" + hours + " jam&a."));
        openQuestDetail(player, def.id());
    }

    private void extendLimited(Player player, QuestDefinition def, int hours) {
        LimitedQuestWindow window = availability.limitedWindow(def.id()).orElse(null);
        if (window == null) {
            player.sendMessage(color("&cQuest belum memiliki schedule. Gunakan Start terlebih dahulu."));
            return;
        }
        long newEnd = window.endAt() + hours * 3600L;
        if (!availability.updateLimitedEnd(def.id(), newEnd)) {
            player.sendMessage(color("&cGagal memperpanjang schedule."));
            return;
        }
        journals.refreshQuestForOnlinePlayers(def.id());
        player.sendMessage(color("&aEnd time ditambah &f" + hours + " jam&a."));
        openQuestDetail(player, def.id());
    }

    private void clearLimited(Player player, QuestDefinition def) {
        if (!availability.clearLimitedWindow(def.id())) {
            player.sendMessage(color("&eQuest ini belum memiliki schedule."));
            return;
        }
        journals.refreshQuestForOnlinePlayers(def.id());
        player.sendMessage(color("&aSchedule &f" + def.title() + " &atelah dihapus."));
        openQuestDetail(player, def.id());
    }

    private ItemStack questIcon(QuestDefinition def) {
        Material material = switch (def.type()) {
            case STORY -> Material.WRITTEN_BOOK;
            case DAILY -> Material.SUNFLOWER;
            case LIMITED -> Material.CLOCK;
        };
        List<String> lore = new ArrayList<>();
        lore.add("&7ID: &f" + def.id());
        lore.add("&7Type: &f" + def.type().name());
        lore.add("&7Objectives: &f" + def.objectives().size());
        lore.add("&7Time Limit: &f" + (def.timeLimitSeconds() <= 0 ? "None" : formatRemaining(def.timeLimitSeconds())));
        lore.add("&7Abandon: " + (def.abandonAllowed() ? "&aALLOWED" : "&cDISABLED"));
        lore.add("&7Cooldown: &f" + (def.cooldownSeconds() <= 0 ? "None" : formatRemaining(def.cooldownSeconds())));
        NpcBinding binding = bindings.binding(def.id()).orElse(null);
        lore.add("&7NPC: " + (binding == null ? "&cUNBOUND" : "&a" + binding.npcName()));
        if (def.type() == QuestType.LIMITED) {
            LimitedQuestWindow window = availability.limitedWindow(def.id()).orElse(null);
            lore.add("&7Schedule: " + limitedStatus(window));
        }
        lore.add("");
        lore.add("&eKlik untuk manage.");
        return item(material, "&f&l" + def.title(), lore.toArray(String[]::new));
    }

    private ItemStack limitedStatusItem(QuestDefinition def, LimitedQuestWindow window) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Status: " + limitedStatus(window));
        if (window != null) {
            lore.add("&7Start: &f" + availability.formatAt(window.startAt()));
            lore.add("&7End: &f" + availability.formatAt(window.endAt()));
        }
        lore.add("");
        lore.add("&7Exact date/time:");
        lore.add("&f/cqj limited set " + def.id() + " ...");
        return item(Material.CLOCK, "&d&lLimited Schedule", lore.toArray(String[]::new));
    }

    private Material historyMaterial(TerminalOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> Material.LIME_DYE;
            case FAILED -> Material.RED_DYE;
            case EXPIRED -> Material.CLOCK;
            case ABANDONED -> Material.BARRIER;
        };
    }

    private String limitedStatus(LimitedQuestWindow window) {
        if (window == null) return "&cNOT CONFIGURED";
        long now = Instant.now().getEpochSecond();
        if (now < window.startAt()) return "&eSCHEDULED";
        if (now >= window.endAt()) return "&cENDED";
        return "&aACTIVE";
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) lines.add(color(line));
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String formatRemaining(long seconds) {
        if (seconds == Long.MAX_VALUE) return "No limit";
        seconds = Math.max(0L, seconds);
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) return "%dd %02dh %02dm".formatted(days, hours, minutes);
        if (hours > 0) return "%dh %02dm %02ds".formatted(hours, minutes, secs);
        return "%02dm %02ds".formatted(minutes, secs);
    }
}
