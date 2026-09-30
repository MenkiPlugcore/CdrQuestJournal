package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.logging.Level;

public final class ConfigMigrationService {
    public static final int CURRENT_SCHEMA = 1;

    public record Result(int fromVersion, int toVersion, int addedKeys, File backupFile) {
        public boolean migrated() { return fromVersion < toVersion || addedKeys > 0; }
    }

    private final CdrQuestJournalPlugin plugin;

    public ConfigMigrationService(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
    }

    public Result migrate() {
        plugin.reloadConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration disk = configFile.exists()
                ? YamlConfiguration.loadConfiguration(configFile)
                : new YamlConfiguration();
        int from = Math.max(0, disk.getInt("config-version", 0));
        int added = 0;
        File backup = null;

        try (InputStream input = plugin.getResource("config.yml")) {
            if (input == null) {
                plugin.getLogger().warning("Bundled config.yml tidak ditemukan; config migration dilewati.");
                return new Result(from, from, 0, null);
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));

            boolean needsWrite = from < CURRENT_SCHEMA;
            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path)) continue;
                if (!disk.contains(path)) {
                    plugin.getConfig().set(path, defaults.get(path));
                    added++;
                    needsWrite = true;
                }
            }

            if (!needsWrite) return new Result(from, from, 0, null);

            if (configFile.exists() && from < CURRENT_SCHEMA) {
                backup = new File(plugin.getDataFolder(),
                        "config-backup-v" + from + "-" + Instant.now().getEpochSecond() + ".yml");
                try {
                    Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Gagal membuat backup config sebelum migration.", ex);
                    backup = null;
                }
            }

            plugin.getConfig().set("config-version", CURRENT_SCHEMA);
            plugin.saveConfig();
            plugin.reloadConfig();
            return new Result(from, CURRENT_SCHEMA, added, backup);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Gagal membaca bundled config untuk migration.", ex);
            return new Result(from, from, 0, backup);
        }
    }
}
