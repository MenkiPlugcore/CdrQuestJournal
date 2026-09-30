# CdrQuestJournal

CdrQuestJournal is a per-player RPG quest-journal layer for Paper servers. BetonQuest remains the quest engine/source of truth; CdrQuestJournal handles physical journals, lifecycle state, Citizens quest-giver binding, safe turn-in transactions, story progression, quest history, cooldowns, crossplay-safe presentation, admin tools, and reliability guards.

Originally built for MoonSign S2 by CADERA / MENKIESTES.

## v1.0.0 — Production Release

Version 1.0.0 freezes the current core architecture as the production baseline.

Core systems:

- Per-player STORY / DAILY / LIMITED quests.
- Persistent objective progress and timers.
- UUID-bound protected written Quest Journals.
- Citizens quest-giver binding by NPC ID/UUID.
- Strict NPC start/turn-in validation.
- Safe `PREPARED -> REWARDED -> COMMITTED` turn-in transactions.
- Restart/crash recovery for pending turn-ins.
- STORY chains with `requires-all` and `requires-any` prerequisites.
- Persistent story completion.
- Persistent quest history: `COMPLETED`, `FAILED`, `EXPIRED`, `ABANDONED`.
- NPC-only abandon flow.
- Generic per-quest cooldowns.
- DAILY cycle locking and LIMITED event scheduling.
- Crossplay-safe Java/Bedrock journal UI.
- Admin GUI for quests, limited schedules, NPC bindings, sessions, history, cooldowns, and restore tools.
- Config migration with backups.
- Startup diagnostics, circular story-chain detection, duplicate/orphan journal sanitation, and conservative orphan-session recovery.

Party/shared quest state is intentionally not implemented. Quest state is personal to each player.

## Requirements

- Java 21
- Paper 1.21.11
- Citizens API / Citizens compatible with 2.0.44-SNAPSHOT
- BetonQuest 3.2.0 for quest gameplay integration
- CdrReputation optional
- Geyser/Floodgate optional

See `docs/PRODUCTION.md` for install, upgrade, rollback, and pre-launch checks.

## Quest definition example

```yaml
quests:
  lost_cargo:
    title: "Lost Cargo"
    type: "STORY"
    giver: "Harbor Master"
    time-limit-seconds: 2700

    story:
      repeatable: false
      requires-all: []
      requires-any: []

    abandon:
      allowed: true

    cooldown:
      seconds: 0

    description:
      - "Temukan kapal karam."
      - "Ambil kembali muatan yang hilang."

    objectives:
      cargo:
        text: "Ambil Lost Cargo"
        target: 12

    rewards:
      - "+25 Reputation"
      - "3x Iron Ingot"

    failure:
      - "-10 Reputation"

    expiration:
      - "-15 Reputation"
```

## BetonQuest actions

```text
cdrjournal_start <questId>
cdrjournal_progress <questId> <objectiveId> <add|set> <amount>
cdrjournal_prepare <questId>
cdrjournal_finalize <questId>
cdrjournal_abort <questId>
cdrjournal_fail <questId>
cdrjournal_expire <questId>
cdrjournal_abandon <questId>
```

Legacy `cdrjournal_turnin` exists for compatibility but is disabled by default. Production deployments should use the safe transaction flow.

Recommended turn-in sequence:

```yaml
actions:
  prepare: cdrjournal_prepare lost_cargo
  reward_rep: cdrrep_add 25 "QUEST_COMPLETED"
  reward_items: give iron_ingot:3
  finalize: cdrjournal_finalize lost_cargo
  abort: cdrjournal_abort lost_cargo
```

## BetonQuest conditions

```text
cdrjournal_active <questId>
cdrjournal_ready <questId>
cdrjournal_expired <questId>
cdrjournal_available <questId>
cdrjournal_correct_npc <questId>
cdrjournal_story_completed <questId>
cdrjournal_history_completed <questId>
cdrjournal_can_abandon <questId>
cdrjournal_cooldown_ready <questId>
```

## Story progression

Linear prerequisite:

```yaml
story:
  repeatable: false
  requires-all:
    - lost_cargo
  requires-any: []
```

Branch/convergence prerequisite:

```yaml
story:
  repeatable: false
  requires-all: []
  requires-any:
    - royal_route
    - outlaw_route
```

Story completion is recorded only after safe turn-in COMMIT.

## Daily and Limited quests

DAILY quests use the configured timezone/reset cycle. Default:

```yaml
lifecycle:
  timezone: "Asia/Jakarta"
  daily-reset: "00:00"
```

LIMITED quests are scheduled by admin through `/cqj`, the admin GUI, or exact commands such as:

```text
/cqj limited set <questId> <yyyy-MM-dd_HH:mm> <yyyy-MM-dd_HH:mm>
/cqj limited end <questId> <yyyy-MM-dd_HH:mm>
/cqj limited now <questId> <durationHours>
```

## Admin

Open the admin dashboard:

```text
/cqj
```

or:

```text
/cqj gui
```

Permission:

```text
cdrquestjournal.admin
```

The GUI manages quest definitions, limited schedules, Citizens bindings, online player sessions, quest history, cooldowns, and journal restore operations.

## Reliability defaults

```yaml
config-version: 1

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

Orphan sessions are intentionally retained by default. Temporarily removing or breaking a quest definition therefore does not automatically destroy player progress.

## Persistent files

Operational player/server state may include:

```text
sessions.yml
story-progress.yml
quest-history.yml
lifecycle.yml
limited.yml
npc-bindings.yml
pending-turnins.yml
turnin-audit.log
```

Back up the entire `plugins/CdrQuestJournal/` directory before upgrades.

## Build

```bash
mvn clean verify
```

Output:

```text
target/CdrQuestJournal-1.0.0.jar
```

CI validates that critical resources and core classes are present in the generated JAR before uploading the artifact.

## Versioning

After 1.0.0:

- `1.0.x` — backward-compatible fixes.
- `1.x.0` — backward-compatible features.
- `2.0.0` — breaking changes.

See `CHANGELOG.md` for release history.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
