# Production Deployment Guide

CdrQuestJournal 1.0.0 targets Paper 1.21.11 / Java 21 with BetonQuest 3.2.0 and Citizens API 2.0.44-SNAPSHOT.

## Required / expected plugins

- Citizens: required by CdrQuestJournal.
- BetonQuest: strongly expected for gameplay; without it, the journal core can load but quest actions/conditions are not registered.
- CdrReputation: optional; use it through BetonQuest when reputation rewards/conditions are needed.
- Geyser/Floodgate: optional. When Geyser is detected, the default reliability guard keeps written-journal formatting crossplay-safe.

## Fresh install

1. Stop the server.
2. Install Citizens and BetonQuest first.
3. Place `CdrQuestJournal-1.0.0.jar` in `plugins/`.
4. Start the server once to generate files.
5. Configure `plugins/CdrQuestJournal/quests.yml`.
6. Bind quest-giver NPCs through `/cqj` or `/cqj npc bind <questId>`.
7. Configure BetonQuest conversations/events.
8. Review startup diagnostics before opening the server to players.

## Upgrade from 0.x

1. Stop the server before replacing the JAR.
2. Back up the entire `plugins/CdrQuestJournal/` directory.
3. Replace the old JAR with `CdrQuestJournal-1.0.0.jar`.
4. Start the server.
5. Allow config migration to add missing defaults. Existing values are preserved where possible and schema migration creates a timestamped config backup.
6. Read the startup diagnostics and resolve unresolved NPC bindings or circular STORY dependencies before gameplay resumes.

Do not delete `sessions.yml`, `story-progress.yml`, `quest-history.yml`, `lifecycle.yml`, `limited.yml`, `npc-bindings.yml`, or `pending-turnins.yml` during an upgrade unless intentionally resetting that data.

## Production defaults

Recommended defaults are already conservative:

```yaml
journal-ui:
  crossplay-safe: true

turn-in:
  allow-legacy-action: false
  prepare-timeout-seconds: 120
  audit-log: true

reliability:
  debug: false
  log-details: false
  cleanup-duplicate-journals: true
  cleanup-orphan-journals: true
  force-crossplay-safe-with-geyser: true
  orphan-session-policy: "KEEP"
```

Keep `allow-legacy-action: false` in production. Use the safe BetonQuest sequence:

```yaml
actions:
  prepare: cdrjournal_prepare lost_cargo
  reward_rep: cdrrep_add 25 "QUEST_COMPLETED"
  reward_items: give iron_ingot:3
  finalize: cdrjournal_finalize lost_cargo
  abort: cdrjournal_abort lost_cargo
```

## Pre-launch checklist

- Server starts without CdrQuestJournal exceptions.
- Startup diagnostics report no circular STORY dependencies.
- Every quest that requires strict NPC interaction has a resolved Citizens binding.
- All LIMITED quests that should be playable have a valid schedule.
- BetonQuest test conversations can start, progress, prepare, reward, and finalize a quest.
- Restart recovery has been tested with an active journal.
- Java player can read/turn in the journal.
- Bedrock player through Geyser can read/turn in the journal.
- Inventory-full safe turn-in has been tested.
- Duplicate journal sanitation has been tested on a staging player.
- `turnin-audit.log` is writable.

## Rollback

If 1.0.0 must be rolled back:

1. Stop the server.
2. Restore the backed-up plugin directory if data/config changes must also be reverted.
3. Restore the previous plugin JAR.
4. Start the server and verify session/transaction state before allowing players to reconnect.

Avoid rolling back while players are actively turning in quests. Pending transaction files are version-sensitive operational data and should be backed up together with the rest of the plugin directory.

## Versioning after 1.0.0

- `1.0.x`: compatible bug/security/reliability fixes.
- `1.x.0`: backward-compatible features.
- `2.0.0`: breaking configuration/API/gameplay changes.
