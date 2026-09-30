package com.menkiestes.cdrquestjournal.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

@SuppressWarnings("deprecation")
public final class MessageService {
    private volatile FileConfiguration config;

    public MessageService(FileConfiguration config) { this.config = config; }
    public void reload(FileConfiguration config) { this.config = config; }

    public String text(String key) {
        String prefix = config.getString("messages.prefix", "");
        String raw = config.getString("messages." + key, key);
        return color(prefix + raw);
    }

    public String text(String key, Map<String, String> replacements) {
        String value = text(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return value;
    }

    public void send(Player player, String key) { player.sendMessage(text(key)); }
    public void send(Player player, String key, Map<String, String> replacements) { player.sendMessage(text(key, replacements)); }

    private static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
}
