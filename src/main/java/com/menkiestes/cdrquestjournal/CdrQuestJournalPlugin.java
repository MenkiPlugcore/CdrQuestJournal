package com.menkiestes.cdrquestjournal;

import com.menkiestes.cdrquestjournal.command.JournalAdminCommand;
import com.menkiestes.cdrquestjournal.config.QuestRegistry;
import com.menkiestes.cdrquestjournal.integration.betonquest.BetonQuestBootstrap;
import com.menkiestes.cdrquestjournal.listener.CitizensNpcInteractionListener;
import com.menkiestes.cdrquestjournal.listener.JournalProtectionListener;
import com.menkiestes.cdrquestjournal.listener.JournalUiListener;
import com.menkiestes.cdrquestjournal.service.AdminGuiService;
import com.menkiestes.cdrquestjournal.service.JournalService;
import com.menkiestes.cdrquestjournal.service.JournalUiService;
import com.menkiestes.cdrquestjournal.service.NpcBindingService;
import com.menkiestes.cdrquestjournal.service.QuestAvailabilityService;
import com.menkiestes.cdrquestjournal.service.TurnInAuditLog;
import com.menkiestes.cdrquestjournal.service.TurnInCoordinator;
import com.menkiestes.cdrquestjournal.storage.LifecycleStore;
import com.menkiestes.cdrquestjournal.storage.LimitedScheduleStore;
import com.menkiestes.cdrquestjournal.storage.SessionStore;
import com.menkiestes.cdrquestjournal.storage.TurnInTransactionStore;
import com.menkiestes.cdrquestjournal.util.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CdrQuestJournalPlugin extends JavaPlugin {
    private QuestRegistry questRegistry;
    private MessageService messages;
    private JournalService journalService;
    private JournalUiService journalUiService;
    private QuestAvailabilityService availabilityService;
    private NpcBindingService npcBindingService;
    private TurnInCoordinator turnInCoordinator;
    private AdminGuiService adminGuiService;
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
        npcBindingService = new NpcBindingService(this, messages);

        SessionStore sessionStore = new SessionStore(this);
        journalService = new JournalService(this, questRegistry, sessionStore, messages, availabilityService);
        journalUiService = new JournalUiService(this, journalService, availabilityService, npcBindingService);
        TurnInTransactionStore turnInStore = new TurnInTransactionStore(this);
        turnInCoordinator = new TurnInCoordinator(this, journalService, npcBindingService, turnInStore,
                new TurnInAuditLog(this), messages);
        adminGuiService = new AdminGuiService(this, journalService, availabilityService, npcBindingService);

        getServer().getPluginManager().registerEvents(new JournalProtectionListener(this, journalService, turnInCoordinator), this);
        getServer().getPluginManager().registerEvents(new CitizensNpcInteractionListener(npcBindingService, adminGuiService), this);
        getServer().getPluginManager().registerEvents(new JournalUiListener(this, journalUiService), this);
        getServer().getPluginManager().registerEvents(adminGuiService, this);

        JournalAdminCommand adminCommand = new JournalAdminCommand(this, journalService, messages, availabilityService, npcBindingService, adminGuiService);
        PluginCommand command = getCommand("cdrjournal");
        if (command == null) throw new IllegalStateException("Command cdrjournal missing from plugin.yml");
        command.setExecutor(adminCommand);
        command.setTabCompleter(adminCommand);

        if (getServer().getPluginManager().getPlugin("BetonQuest") != null) {
            if (BetonQuestBootstrap.register(this, journalService, npcBindingService, turnInCoordinator, availabilityService)) {
                getLogger().info("BetonQuest integration registered.");
            }
        } else {
            getLogger().warning("BetonQuest tidak ditemukan. Journal core tetap aktif, tetapi quest actions tidak terdaftar.");
        }

        startRefreshTask();
        if (getConfig().getBoolean("journal.restore-on-join", true)) {
            getServer().getOnlinePlayers().forEach(player -> {
                turnInCoordinator.recoverPlayer(player);
                journalService.restorePlayer(player);
                journalUiService.refreshAll(player);
            });
        }

        getLogger().info("Citizens NPC quest giver binding enabled.");
        getLogger().info("Safe turn-in transaction recovery enabled.");
        getLogger().info("Crossplay-safe polished journal UI enabled.");
        getLogger().info("Quest Admin GUI enabled.");
        getLogger().info("Persistent story chain progression enabled.");
        getLogger().info("CdrQuestJournal v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        if (turnInCoordinator != null) turnInCoordinator.save();
        if (journalService != null) journalService.save();
    }

    public void reloadAll() {
        reloadConfig();
        questRegistry.reload();
        messages.reload(getConfig());
        availabilityService.reload();
        npcBindingService.reload();
        startRefreshTask();
        getServer().getOnlinePlayers().forEach(player -> {
            turnInCoordinator.recoverPlayer(player);
            journalService.restorePlayer(player);
            journalUiService.refreshAll(player);
        });
    }

    public QuestRegistry getQuestRegistry() { return questRegistry; }
    public QuestAvailabilityService getAvailabilityService() { return availabilityService; }
    public NpcBindingService getNpcBindingService() { return npcBindingService; }
    public TurnInCoordinator getTurnInCoordinator() { return turnInCoordinator; }
    public JournalUiService getJournalUiService() { return journalUiService; }
    public AdminGuiService getAdminGuiService() { return adminGuiService; }

    private void startRefreshTask() {
        if (refreshTask != null) refreshTask.cancel();
        long seconds = Math.max(1L, getConfig().getLong("journal.refresh-seconds", 5L));
        long ticks = seconds * 20L;
        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (var player : getServer().getOnlinePlayers()) {
                turnInCoordinator.tickPlayer(player);
                journalService.tickPlayer(player);
                journalUiService.refreshAll(player);
            }
        }, ticks, ticks);
    }
}
