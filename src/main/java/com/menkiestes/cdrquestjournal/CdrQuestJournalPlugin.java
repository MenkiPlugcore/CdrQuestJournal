package com.menkiestes.cdrquestjournal;

import com.menkiestes.cdrquestjournal.command.JournalAdminCommand;
import com.menkiestes.cdrquestjournal.config.QuestRegistry;
import com.menkiestes.cdrquestjournal.integration.betonquest.BetonQuestBootstrap;
import com.menkiestes.cdrquestjournal.listener.JournalProtectionListener;
import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.QuestAvailabilityService;
import com.menkiestes.cdrquestjournal.storage.LifecycleStore;
import com.menkiestes.cdrquestjournal.storage.LimitedScheduleStore;
import com.menkiestes.cdrquestjournal.storage.SessionStore;
import com.menkiestes.cdrquestjournal.util.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CdrQuestJournalPlugin extends JavaPlugin {
    private QuestRegistry questRegistry;
    private MessageService messages;
    private JournalService journalService;
    private QuestAvailabilityService availabilityService;
    private BukkitTask refreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("quests.yml", false);

        questRegistry = new QuestRegistry(this);
        questRegistry.reload();
        messages = new MessageService(getConfig());

        LimitedScheduleStore limitedStore = new LimitedScheduleStore(this);
        LifecycleStore lifecycleStore = new LifecycleStore(this);
        availabilityService = new QuestAvailabilityService(this, limitedStore, lifecycleStore);

        SessionStore sessionStore = new SessionStore(this);
        journalService = new JournalService(this, questRegistry, sessionStore, messages, availabilityService);

        getServer().getPluginManager().registerEvents(new JournalProtectionListener(this, journalService), this);

        JournalAdminCommand adminCommand = new JournalAdminCommand(this, journalService, messages, availabilityService);
        PluginCommand command = getCommand("cdrjournal");
        if (command == null) throw new IllegalStateException("Command cdrjournal missing from plugin.yml");
        command.setExecutor(adminCommand);
        command.setTabCompleter(adminCommand);

        if (getServer().getPluginManager().getPlugin("BetonQuest") != null) {
            if (BetonQuestBootstrap.register(this, journalService)) {
                getLogger().info("BetonQuest integration registered.");
            }
        } else {
            getLogger().warning("BetonQuest tidak ditemukan. Journal core tetap aktif, tetapi quest actions tidak terdaftar.");
        }

        startRefreshTask();
        if (getConfig().getBoolean("journal.restore-on-join", true)) {
            getServer().getOnlinePlayers().forEach(journalService::restorePlayer);
        }

        getLogger().info("CdrQuestJournal v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        if (journalService != null) journalService.save();
    }

    public void reloadAll() {
        reloadConfig();
        questRegistry.reload();
        messages.reload(getConfig());
        availabilityService.reload();
        startRefreshTask();
        getServer().getOnlinePlayers().forEach(journalService::restorePlayer);
    }

    public QuestRegistry getQuestRegistry() {
        return questRegistry;
    }

    public QuestAvailabilityService getAvailabilityService() {
        return availabilityService;
    }

    private void startRefreshTask() {
        if (refreshTask != null) refreshTask.cancel();
        long seconds = Math.max(1L, getConfig().getLong("journal.refresh-seconds", 5L));
        long ticks = seconds * 20L;
        refreshTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> getServer().getOnlinePlayers().forEach(journalService::tickPlayer),
                ticks,
                ticks
        );
    }
}
