package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.TurnInTransaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.logging.Level;

public final class TurnInAuditLog {
    private final CdrQuestJournalPlugin plugin;

    public TurnInAuditLog(CdrQuestJournalPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void log(String event, TurnInTransaction tx, String detail) {
        if (!plugin.getConfig().getBoolean("turn-in.audit-log", true)) return;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;
        String safeDetail = sanitize(detail);
        String line = "%s\t%s\t%s\t%s\t%s\t%s\t%s%n".formatted(
                Instant.now(),
                sanitize(event),
                tx.transactionId(),
                tx.playerId(),
                sanitize(tx.questId()),
                tx.state().name(),
                safeDetail
        );
        try {
            Files.writeString(
                    plugin.getDataFolder().toPath().resolve("turnin-audit.log"),
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not append turn-in audit log", ex);
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
