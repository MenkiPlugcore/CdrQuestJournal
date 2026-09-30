package com.menkiestes.cdrquestjournal.model;

import net.citizensnpcs.api.npc.NPC;

import java.util.UUID;

public record NpcBinding(String questId, int npcId, UUID npcUuid, String npcName) {
    public NpcBinding {
        if (questId == null || questId.isBlank()) throw new IllegalArgumentException("questId cannot be blank");
        questId = questId.toLowerCase();
        npcName = npcName == null || npcName.isBlank() ? "NPC #" + npcId : npcName;
    }

    public boolean matches(NPC npc) {
        if (npc == null) return false;
        if (npcUuid != null && npc.getUniqueId() != null) return npcUuid.equals(npc.getUniqueId());
        return npcId == npc.getId();
    }
}
