# CdrQuestJournal

Quest Journal bridge for MoonSign S2. BetonQuest remains the quest engine/source of truth; CdrQuestJournal handles physical journals, personal quest lifecycle, Citizens quest-giver binding, safe turn-in transactions, story progression, persistent quest history, cooldowns, crossplay-safe journal presentation, and admin management.

## v0.9.0 — Quest History, Abandon & Cooldown

All quest progress remains per-player. Party/shared quest state is intentionally not implemented.

### Persistent quest history

Terminal outcomes are persisted to `plugins/CdrQuestJournal/quest-history.yml`:

- `COMPLETED`
- `FAILED`
- `EXPIRED`
- `ABANDONED`

Each history entry stores quest ID/type, start/end time, Quest Giver, cycle/event key, and outcome. Duplicate terminal callbacks for the same quest attempt are deduplicated.

The Admin GUI player inspector now includes **Quest History** and **Active Cooldowns** views.

### NPC-only abandon

Configure per quest:

```yaml
abandon:
  allowed: true
```

Critical story quests can disable it:

```yaml
abandon:
  allowed: false
```

Player-facing abandon is still NPC-based. Use a BetonQuest conversation confirmation and then:

```yaml
actions:
  abandon_lost_cargo: cdrjournal_abandon lost_cargo
```

`cdrjournal_abandon` validates that the player recently clicked the bound Citizens Quest Giver before closing the session and removing the journal.

Optional reputation/item penalties should remain in the BetonQuest event chain, keeping CdrQuestJournal independent from reward policy.

### Generic cooldown

Configure a cooldown in seconds:

```yaml
cooldown:
  seconds: 21600
```

This example locks the quest for six hours after a terminal outcome. A value of `0` disables cooldown.

Cooldown state is derived from persistent quest history, so restart/reload cannot desynchronize a separate timer file.

### BetonQuest conditions

```yaml
conditions:
  completed_before: cdrjournal_history_completed lost_cargo
  can_abandon: cdrjournal_can_abandon lost_cargo
  cooldown_ready: cdrjournal_cooldown_ready lost_cargo
  story_done: cdrjournal_story_completed lost_cargo
  available: cdrjournal_available lost_cargo
```

`cdrjournal_available` includes story prerequisites, one-time story completion, DAILY/LIMITED lifecycle locks, and configured cooldowns.

## Story chain / progression

STORY definitions support persistent prerequisites:

```yaml
story:
  repeatable: false
  requires-all:
    - lost_cargo
  requires-any: []
```

or branching/converging prerequisites:

```yaml
story:
  repeatable: false
  requires-all: []
  requires-any:
    - royal_route
    - outlaw_route
```

Story completion is recorded only after the safe turn-in reaches COMMIT.

## Admin GUI

Run `/cqj` or `/cqj gui` in-game with `cdrquestjournal.admin`.

The dashboard provides:

- Quest Manager for STORY / DAILY / LIMITED definitions.
- Limited Quest Manager with start/extend/clear controls.
- Player Sessions with active journals, quest history, active cooldowns, and restore tools.
- Citizens NPC binding selection.
- Reload.

Exact LIMITED date/time scheduling remains available through admin commands:

```text
/cqj limited set <questId> <yyyy-MM-dd_HH:mm> <yyyy-MM-dd_HH:mm>
/cqj limited end <questId> <yyyy-MM-dd_HH:mm>
```

## Safe turn-in

Recommended BetonQuest flow:

```yaml
actions:
  prepare: cdrjournal_prepare lost_cargo
  reward_rep: cdrrep_add 25 "QUEST_COMPLETED"
  reward_items: give iron_ingot:3
  finalize: cdrjournal_finalize lost_cargo
  abort: cdrjournal_abort lost_cargo
```

The transaction flow is `PREPARED -> REWARDED -> COMMITTED`, with pending recovery after restart.

## Existing systems

- Per-player STORY / DAILY / LIMITED lifecycle.
- Persistent objective progress and timers.
- Protected UUID-bound written journals.
- Citizens NPC giver binding.
- Two-phase safe turn-in and pending recovery.
- Story chains and persistent story completion.
- Quest history, NPC abandon, and generic cooldown.
- Crossplay-safe polished written-book UI.
- Admin GUI.
- BetonQuest 3.x actions/conditions.

## Build

Requires JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

Output: `target/CdrQuestJournal-0.9.0.jar`.

Target: Paper 1.21.11, BetonQuest 3.2.0, Citizens API 2.0.44-SNAPSHOT.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
