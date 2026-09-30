package com.menkiestes.cdrquestjournal.command;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.QuestDefinition;
import com.menkiestes.cdrquestjournal.model.QuestSession;
import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.util.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JournalAdminCommand implements CommandExecutor, TabCompleter {
    private final CdrQuestJournalPlugin plugin;
    private final JournalService service;
    private final MessageService messages;
    public JournalAdminCommand(CdrQuestJournalPlugin plugin, JournalService service, MessageService messages) { this.plugin = plugin; this.service = service; this.messages = messages; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cdrquestjournal.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length == 0) { sender.sendMessage("§7/cqj <reload|inspect|restore>"); return true; }
        switch (args[0].toLowerCase()) {
            case "reload" -> { plugin.reloadAll(); sender.sendMessage(messages.text("reload")); }
            case "inspect" -> inspect(sender, args);
            case "restore" -> restore(sender, args);
            default -> sender.sendMessage("§7/cqj <reload|inspect|restore>");
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
        long now = Instant.now().getEpochSecond();
        for (QuestSession session : sessions) {
            String name = service.definition(session.questId()).map(QuestDefinition::title).orElse(session.questId());
            String time = session.hasDeadline() ? session.remainingSeconds(now) + "s" : "no limit";
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

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("cdrquestjournal.admin")) return List.of();
        if (args.length == 1) return filter(List.of("reload", "inspect", "restore"), args[0]);
        if (args.length == 2 && ("inspect".equalsIgnoreCase(args[0]) || "restore".equalsIgnoreCase(args[0]))) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && "restore".equalsIgnoreCase(args[0])) return plugin.getQuestRegistry().all().stream().map(QuestDefinition::id).filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String value : values) if (value.toLowerCase().startsWith(lower)) result.add(value);
        return result;
    }
}
