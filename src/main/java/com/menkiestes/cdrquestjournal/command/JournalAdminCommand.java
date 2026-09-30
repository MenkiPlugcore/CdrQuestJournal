package com.menkiestes.cdrquestjournal.command;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.LimitedQuestWindow;
import com.menkiestes.cdrquestjournal.model.NpcBinding;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.model.QuestType;
import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import com.menkiestes.cdrquestjournal.service.QuestAvailabilityService;
import com.menkiestes.cdrquestjournal.util.MessageService;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JournalAdminCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm");

    private final CdrQuestJournalPlugin plugin;
    private final JournalService service;
    private final MessageService messages;
    private final QuestAvailabilityService availability;
    private final NpcBindingService bindings;

    public JournalAdminCommand(CdrQuestJournalPlugin plugin, JournalService service, MessageService messages,
                               QuestAvailabilityService availability, NpcBindingService bindings) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
        this.availability = availability;
        this.bindings = bindings;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cdrquestjournal.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> { plugin.reloadAll(); sender.sendMessage(messages.text("reload")); }
            case "inspect" -> inspect(sender, args);
            case "restore" -> restore(sender, args);
            case "limited" -> limited(sender, args);
            case "npc" -> npc(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void inspect(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§7/cqj inspect <player>"); return; }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) { sender.sendMessage("§cPlayer harus online."); return; }
        var sessions = service.sessions(player.getUniqueId());
        if (sessions.isEmpty()) { sender.sendMessage(messages.text("no-active")); return; }
        sender.sendMessage(messages.text("inspect-header", Map.of("player", player.getName())));
        for (QuestSession session : sessions) {
            String name = service.definition(session.questId()).map(QuestDefinition::title).orElse(session.questId());
            long remaining = service.remainingSeconds(session);
            String time = remaining == Long.MAX_VALUE ? "no limit" : remaining + "s";
            sender.sendMessage(messages.text("inspect-line", Map.of("quest", name, "status", session.status().name(), "time", time)));
        }
    }

    private void restore(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§7/cqj restore <player> [questId]"); return; }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) { sender.sendMessage("§cPlayer harus online."); return; }
        String questId = args.length >= 3 ? args[2] : null;
        if (!service.restore(player, questId)) { sender.sendMessage("§cTidak ada journal aktif yang cocok."); return; }
        sender.sendMessage(messages.text("restored"));
    }

    private void npc(CommandSender sender, String[] args) {
        if (args.length < 2) { sendNpcUsage(sender); return; }
        switch (args[1].toLowerCase()) {
            case "bind" -> npcBind(sender, args);
            case "unbind" -> npcUnbind(sender, args);
            case "info" -> npcInfo(sender, args);
            default -> sendNpcUsage(sender);
        }
    }

    private void npcBind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cCommand bind harus dijalankan in-game."); return; }
        if (args.length < 3) { sender.sendMessage("§7/cqj npc bind <questId>"); return; }
        QuestDefinition definition = plugin.getQuestRegistry().get(args[2]).orElse(null);
        if (definition == null) { sender.sendMessage("§cQuest tidak ditemukan."); return; }
        NPC npc = bindings.findLookedAtNpc(player).orElse(null);
        if (npc == null) {
            sender.sendMessage("§cLihat langsung ke Citizens NPC lalu jalankan command ini lagi.");
            return;
        }
        NpcBinding binding = bindings.bind(definition.id(), npc);
        sender.sendMessage("§aQuest §f" + definition.title() + " §aterikat ke NPC §f" + binding.npcName() + " §8(ID " + binding.npcId() + ").");
    }

    private void npcUnbind(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§7/cqj npc unbind <questId>"); return; }
        QuestDefinition definition = plugin.getQuestRegistry().get(args[2]).orElse(null);
        if (definition == null) { sender.sendMessage("§cQuest tidak ditemukan."); return; }
        if (!bindings.unbind(definition.id())) { sender.sendMessage("§eQuest tersebut belum memiliki NPC binding."); return; }
        sender.sendMessage("§aNPC binding untuk §f" + definition.title() + " §atelah dihapus.");
    }

    private void npcInfo(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§7/cqj npc info <questId>"); return; }
        QuestDefinition definition = plugin.getQuestRegistry().get(args[2]).orElse(null);
        if (definition == null) { sender.sendMessage("§cQuest tidak ditemukan."); return; }
        NpcBinding binding = bindings.binding(definition.id()).orElse(null);
        if (binding == null) { sender.sendMessage("§eQuest §f" + definition.title() + " §ebelum terikat ke NPC."); return; }
        boolean exists = bindings.resolveNpc(binding).isPresent();
        sender.sendMessage("§6Quest NPC Binding");
        sender.sendMessage("§7Quest: §f" + definition.title());
        sender.sendMessage("§7NPC: §f" + binding.npcName());
        sender.sendMessage("§7Citizens ID: §f" + binding.npcId());
        sender.sendMessage("§7NPC UUID: §f" + binding.npcUuid());
        sender.sendMessage("§7Resolved: " + (exists ? "§aYES" : "§cNO"));
    }

    private void limited(CommandSender sender, String[] args) {
        if (args.length < 2) { sendLimitedUsage(sender); return; }
        switch (args[1].toLowerCase()) {
            case "set" -> limitedSet(sender, args);
            case "end" -> limitedEnd(sender, args);
            case "now" -> limitedNow(sender, args);
            case "clear" -> limitedClear(sender, args);
            case "info" -> limitedInfo(sender, args);
            default -> sendLimitedUsage(sender);
        }
    }

    private void limitedSet(CommandSender sender, String[] args) {
        if (args.length < 5) { sender.sendMessage("§7/cqj limited set <questId> <yyyy-MM-dd_HH:mm> <yyyy-MM-dd_HH:mm>"); return; }
        QuestDefinition definition = limitedDefinition(sender, args[2]);
        if (definition == null) return;
        Long start = parseTime(sender, args[3]);
        Long end = parseTime(sender, args[4]);
        if (start == null || end == null) return;
        if (end <= start) { sender.sendMessage("§cWaktu end harus setelah start."); return; }
        availability.setLimitedWindow(definition.id(), start, end);
        service.refreshQuestForOnlinePlayers(definition.id());
        sender.sendMessage("§aLimited quest §f" + definition.title() + " §adiatur: §f" + availability.formatAt(start) + " §7→ §f" + availability.formatAt(end));
    }

    private void limitedEnd(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage("§7/cqj limited end <questId> <yyyy-MM-dd_HH:mm>"); return; }
        QuestDefinition definition = limitedDefinition(sender, args[2]);
        if (definition == null) return;
        Long end = parseTime(sender, args[3]);
        if (end == null) return;
        if (!availability.updateLimitedEnd(definition.id(), end)) { sender.sendMessage("§cSchedule belum ada atau end tidak valid."); return; }
        service.refreshQuestForOnlinePlayers(definition.id());
        sender.sendMessage("§aEnd time §f" + definition.title() + " §adiubah ke §f" + availability.formatAt(end));
    }

    private void limitedNow(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage("§7/cqj limited now <questId> <durationHours>"); return; }
        QuestDefinition definition = limitedDefinition(sender, args[2]);
        if (definition == null) return;
        double hours;
        try { hours = Double.parseDouble(args[3]); }
        catch (NumberFormatException ex) { sender.sendMessage("§cDuration harus berupa angka jam."); return; }
        if (hours <= 0D) { sender.sendMessage("§cDuration harus lebih dari 0."); return; }
        long start = Instant.now().getEpochSecond();
        long end = start + Math.max(60L, Math.round(hours * 3600D));
        availability.setLimitedWindow(definition.id(), start, end);
        service.refreshQuestForOnlinePlayers(definition.id());
        sender.sendMessage("§aLimited quest §f" + definition.title() + " §adimulai sekarang sampai §f" + availability.formatAt(end));
    }

    private void limitedClear(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§7/cqj limited clear <questId>"); return; }
        QuestDefinition definition = limitedDefinition(sender, args[2]);
        if (definition == null) return;
        if (!availability.clearLimitedWindow(definition.id())) { sender.sendMessage("§eQuest tersebut belum memiliki schedule."); return; }
        service.refreshQuestForOnlinePlayers(definition.id());
        sender.sendMessage("§aSchedule limited quest §f" + definition.title() + " §atelah dihapus.");
    }

    private void limitedInfo(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§7/cqj limited info <questId>"); return; }
        QuestDefinition definition = limitedDefinition(sender, args[2]);
        if (definition == null) return;
        LimitedQuestWindow window = availability.limitedWindow(definition.id()).orElse(null);
        if (window == null) { sender.sendMessage("§eBelum ada schedule untuk §f" + definition.title()); return; }
        long now = Instant.now().getEpochSecond();
        String status = now < window.startAt() ? "SCHEDULED" : now >= window.endAt() ? "ENDED" : "ACTIVE";
        sender.sendMessage("§6Limited Quest: §f" + definition.title());
        sender.sendMessage("§7Status: §f" + status);
        sender.sendMessage("§7Start: §f" + availability.formatAt(window.startAt()));
        sender.sendMessage("§7End: §f" + availability.formatAt(window.endAt()));
    }

    private QuestDefinition limitedDefinition(CommandSender sender, String questId) {
        QuestDefinition definition = plugin.getQuestRegistry().get(questId).orElse(null);
        if (definition == null) { sender.sendMessage("§cQuest tidak ditemukan."); return null; }
        if (definition.type() != QuestType.LIMITED) { sender.sendMessage("§cQuest §f" + definition.title() + " §cbukan bertipe LIMITED."); return null; }
        return definition;
    }

    private Long parseTime(CommandSender sender, String raw) {
        try { return LocalDateTime.parse(raw, INPUT_FORMAT).atZone(availability.zoneId()).toEpochSecond(); }
        catch (DateTimeException ex) {
            sender.sendMessage("§cFormat waktu salah. Gunakan §fyyyy-MM-dd_HH:mm §7contoh: 2026-10-05_23:59");
            return null;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§7/cqj <reload|inspect|restore|limited|npc>");
    }

    private void sendNpcUsage(CommandSender sender) {
        sender.sendMessage("§6NPC Quest Giver Admin");
        sender.sendMessage("§7/cqj npc bind <questId> §8- lihat NPC terlebih dahulu");
        sender.sendMessage("§7/cqj npc info <questId>");
        sender.sendMessage("§7/cqj npc unbind <questId>");
    }

    private void sendLimitedUsage(CommandSender sender) {
        sender.sendMessage("§6Limited Quest Admin");
        sender.sendMessage("§7/cqj limited set <questId> <start> <end>");
        sender.sendMessage("§7/cqj limited end <questId> <end>");
        sender.sendMessage("§7/cqj limited now <questId> <durationHours>");
        sender.sendMessage("§7/cqj limited info <questId>");
        sender.sendMessage("§7/cqj limited clear <questId>");
        sender.sendMessage("§8Format waktu: yyyy-MM-dd_HH:mm (" + availability.zoneId().getId() + ")");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("cdrquestjournal.admin")) return List.of();
        if (args.length == 1) return filter(List.of("reload", "inspect", "restore", "limited", "npc"), args[0]);
        if (args.length == 2 && ("inspect".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && "limited".equalsIgnoreCase(args[0])) return filter(List.of("set", "end", "now", "info", "clear"), args[1]);
        if (args.length == 2 && "npc".equalsIgnoreCase(args[0])) return filter(List.of("bind", "info", "unbind"), args[1]);
        if (args.length == 3 && "restore".equalsIgnoreCase(args[0])) {
            return filter(plugin.getQuestRegistry().all().stream().map(QuestDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && "limited".equalsIgnoreCase(args[0])) {
            return filter(plugin.getQuestRegistry().all().stream().filter(d -> d.type() == QuestType.LIMITED).map(QuestDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && "npc".equalsIgnoreCase(args[0])) {
            return filter(plugin.getQuestRegistry().all().stream().map(QuestDefinition::id).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String value : values) if (value.toLowerCase().startsWith(lower)) result.add(value);
        return result;
    }
}
