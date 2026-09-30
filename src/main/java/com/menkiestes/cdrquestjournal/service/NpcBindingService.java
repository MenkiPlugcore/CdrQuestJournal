package com.menkiestes.cdrquestjournal.service;

import com.menkiestes.cdrquestjournal.CdrQuestJournalPlugin;
import com.menkiestes.cdrquestjournal.model.NpcBinding;
import com.menkiestes.cdrquestjournal.util.MessageService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class NpcBindingService {
    public enum Validation {
        OK,
        QUEST_NOT_BOUND,
        NO_RECENT_NPC,
        WRONG_NPC
    }

    private record ClickContext(int npcId, UUID npcUuid, long clickedAtMillis) {
        boolean matches(NpcBinding binding) {
            if (binding.npcUuid() != null && npcUuid != null) return binding.npcUuid().equals(npcUuid);
            return binding.npcId() == npcId;
        }
    }

    private final CdrQuestJournalPlugin plugin;
    private final MessageService messages;
    private final File file;
    private final Map<String, NpcBinding> bindings = new LinkedHashMap<>();
    private final Map<UUID, ClickContext> recentClicks = new ConcurrentHashMap<>();

    public NpcBindingService(CdrQuestJournalPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.file = new File(plugin.getDataFolder(), "npc-bindings.yml");
        reload();
    }

    public synchronized void reload() {
        bindings.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("bindings");
        if (root == null) return;

        for (String questId : root.getKeys(false)) {
            int npcId = root.getInt(questId + ".npc-id", -1);
            if (npcId < 0) continue;
            UUID npcUuid = null;
            String uuidRaw = root.getString(questId + ".npc-uuid", "");
            if (uuidRaw != null && !uuidRaw.isBlank()) {
                try { npcUuid = UUID.fromString(uuidRaw); }
                catch (IllegalArgumentException ex) { plugin.getLogger().warning("Invalid NPC UUID for quest " + questId); }
            }
            String npcName = root.getString(questId + ".npc-name", "NPC #" + npcId);
            bindings.put(questId.toLowerCase(), new NpcBinding(questId, npcId, npcUuid, npcName));
        }
    }

    public synchronized NpcBinding bind(String questId, NPC npc) {
        NpcBinding binding = new NpcBinding(questId, npc.getId(), npc.getUniqueId(), npc.getFullName());
        bindings.put(questId.toLowerCase(), binding);
        save();
        return binding;
    }

    public synchronized boolean unbind(String questId) {
        boolean removed = bindings.remove(questId.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public synchronized Optional<NpcBinding> binding(String questId) {
        if (questId == null) return Optional.empty();
        return Optional.ofNullable(bindings.get(questId.toLowerCase()));
    }

    public void recordClick(Player player, NPC npc) {
        recentClicks.put(player.getUniqueId(), new ClickContext(npc.getId(), npc.getUniqueId(), System.currentTimeMillis()));
    }

    public void clearContext(Player player) {
        recentClicks.remove(player.getUniqueId());
    }

    public Validation validateStart(Player player, String questId) {
        boolean required = plugin.getConfig().getBoolean("npc.require-binding-for-start", true);
        return validate(player, questId, required);
    }

    public Validation validateTurnIn(Player player, String questId) {
        boolean required = plugin.getConfig().getBoolean("npc.require-binding-for-turnin", true);
        return validate(player, questId, required);
    }

    public void notifyValidation(Player player, String questId, Validation validation) {
        NpcBinding binding = binding(questId).orElse(null);
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("quest", questId);
        replacements.put("npc", binding == null ? "Quest Giver" : binding.npcName());
        replacements.put("npc_id", binding == null ? "-" : Integer.toString(binding.npcId()));
        switch (validation) {
            case QUEST_NOT_BOUND -> messages.send(player, "npc-not-bound", replacements);
            case NO_RECENT_NPC -> messages.send(player, "npc-click-required", replacements);
            case WRONG_NPC -> messages.send(player, "npc-wrong", replacements);
            case OK -> { }
        }
    }

    public Optional<NPC> findLookedAtNpc(Player player) {
        int maxDistance = Math.max(1, Math.min(120, plugin.getConfig().getInt("npc.bind-look-distance", 8)));
        Entity target = player.getTargetEntity(maxDistance);
        if (target == null) return Optional.empty();
        for (NPCRegistry registry : CitizensAPI.getNPCRegistries()) {
            NPC npc = registry.getNPC(target);
            if (npc != null) return Optional.of(npc);
        }
        return Optional.empty();
    }

    public Optional<NPC> resolveNpc(NpcBinding binding) {
        for (NPCRegistry registry : CitizensAPI.getNPCRegistries()) {
            NPC npc = binding.npcUuid() == null ? null : registry.getByUniqueId(binding.npcUuid());
            if (npc == null) npc = registry.getById(binding.npcId());
            if (npc != null && binding.matches(npc)) return Optional.of(npc);
        }
        return Optional.empty();
    }

    private Validation validate(Player player, String questId, boolean requireBinding) {
        NpcBinding binding = binding(questId).orElse(null);
        if (binding == null) return requireBinding ? Validation.QUEST_NOT_BOUND : Validation.OK;

        ClickContext context = recentClicks.get(player.getUniqueId());
        if (context == null) return Validation.NO_RECENT_NPC;
        long ttlSeconds = Math.max(1L, plugin.getConfig().getLong("npc.interaction-context-seconds", 60L));
        if (System.currentTimeMillis() - context.clickedAtMillis() > ttlSeconds * 1000L) {
            recentClicks.remove(player.getUniqueId());
            return Validation.NO_RECENT_NPC;
        }
        return context.matches(binding) ? Validation.OK : Validation.WRONG_NPC;
    }

    private synchronized void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for NPC bindings.");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (NpcBinding binding : bindings.values()) {
            String base = "bindings." + binding.questId();
            yaml.set(base + ".npc-id", binding.npcId());
            yaml.set(base + ".npc-uuid", binding.npcUuid() == null ? null : binding.npcUuid().toString());
            yaml.set(base + ".npc-name", binding.npcName());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save npc-bindings.yml", ex);
        }
    }
}
