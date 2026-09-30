# CdrQuestJournal

Quest Journal bridge for MoonSign S2. BetonQuest remains the quest engine/source of truth; CdrQuestJournal handles physical journals, personal quest lifecycle, Citizens quest-giver binding, safe turn-in transactions, story progression, persistent quest history, cooldowns, crossplay-safe journal presentation, admin management, and production reliability guards.

## v0.9.5 — Reliability, Crossplay & Migration Polish

This release focuses on production hardening rather than new gameplay.

### Config migration

`config.yml` now has a schema version:

```yaml
config-version: 1
```

Existing installations are migrated automatically. Before a schema migration, the plugin keeps a timestamped backup such as:

```text
config-backup-v0-1759220000.yml
```

Missing keys from the bundled config are merged without intentionally replacing existing custom values.

### Startup diagnostics

On enable/reload the console reports:

- Total STORY / DAILY / LIMITED quest definitions.
- Bound, unresolved, and unbound Citizens NPC quest givers.
- Persisted and orphaned quest sessions.
- Pending safe turn-in transactions.
- STORY chain circular dependencies.
- Crossplay-safe UI state.
- Config schema version.

Orphan sessions are kept by default. A removed or temporarily broken quest definition therefore does not automatically destroy player progress.

### Journal sanitation

On join/respawn/reload the plugin can remove:

- Duplicate physical journals for the same active quest.
- Orphan physical journals with no valid active session/definition.

The authoritative session data is not deleted by this cleanup.

### Crossplay fallback

When Geyser is detected and `reliability.force-crossplay-safe-with-geyser` is enabled, `journal-ui.crossplay-safe` is forced on to avoid unsupported/decorative book formatting for Bedrock players.

```yaml
reliability:
  debug: false
  log-details: false
  cleanup-duplicate-journals: true
  cleanup-orphan-journals: true
  force-crossplay-safe-with-geyser: true
  orphan-session-policy: "KEEP"
```

## v0.9.0 — Quest History, Abandon & Cooldown

All quest progress remains per-player. Party/shared quest state is intentionally not implemented.

Terminal outcomes are persisted to `plugins/CdrQuestJournal/quest-history.yml` as `COMPLETED`, `FAILED`, `EXPIRED`, or `ABANDONED`. Each history entry stores quest ID/type, start/end time, Quest Giver, cycle/event key, and outcome.

Player-facing abandon remains NPC-based:

```yaml
abandon:
  allowed: true

cooldown:
  seconds: 21600
```

BetonQuest hooks include:

```yaml
conditions:
  completed_before: cdrjournal_history_completed lost_cargo
  can_abandon: cdrjournal_can_abandon lost_cargo
  cooldown_ready: cdrjournal_cooldown_ready lost_cargo
  story_done: cdrjournal_story_completed lost_cargo
  available: cdrjournal_available lost_cargo

actions:
  abandon_lost_cargo: cdrjournal_abandon lost_cargo
```

## Story chain / progression

STORY definitions support persistent prerequisites:

```yaml
story:
  repeatable: false
  requires-all:
    - lost_cargo
  requires-any: []
```

Story completion is recorded only after the safe turn-in reaches COMMIT.

## Admin GUI

Run `/cqj` or `/cqj gui` in-game with `cdrquestjournal.admin`.

The dashboard provides Quest Manager, Limited Quest Manager, Player Sessions, Quest History, Active Cooldowns, Citizens NPC binding, journal restore tools, and reload.

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

## Build

Requires JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

Output: `target/CdrQuestJournal-0.9.5.jar`.

Target: Paper 1.21.11, BetonQuest 3.2.0, Citizens API 2.0.44-SNAPSHOT.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
