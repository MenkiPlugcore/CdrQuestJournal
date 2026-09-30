# CdrQuestJournal

Quest Journal bridge for MoonSign S2. BetonQuest remains the quest engine/source of truth; CdrQuestJournal handles the physical journal, lifecycle, Citizens quest-giver binding, and safe turn-in transaction state.

## v0.4.0 — Safe Turn-In & Reward Delivery

Turn-in now uses a two-phase transaction so the journal/session is not closed before the external reward chain reports success.

### Recommended BetonQuest flow

```yaml
actions:
  prepare: cdrjournal_prepare lost_cargo
  reward_rep: cdrrep_add 25 "QUEST_COMPLETED"
  reward_items: give iron_ingot:3
  finalize: cdrjournal_finalize lost_cargo
  abort: cdrjournal_abort lost_cargo
```

Execution order:

```text
NPC click
  -> cdrjournal_prepare
  -> external BetonQuest reward actions
  -> cdrjournal_finalize
  -> journal removed
  -> session closed / lifecycle COMPLETED
```

`cdrjournal_prepare` validates the bound Citizens NPC, READY status, physical journal, transaction lock, and free inventory slots. It persists a `PREPARED` transaction before rewards run.

`cdrjournal_finalize` first persists `REWARDED`, then commits quest completion. If the server stops after the REWARDED marker but before the journal/session is closed, the plugin automatically completes the commit when the player rejoins or the recovery tick runs. This prevents the reward chain from being intentionally replayed after a known-success marker.

`cdrjournal_abort` releases a PREPARED transaction when a reward chain is intentionally cancelled before rewards are confirmed.

> External BetonQuest rewards are not part of one database transaction. A crash between an external reward action and `cdrjournal_finalize` is inherently ambiguous for non-idempotent third-party rewards. v0.4.0 narrows that window and provides durable PREPARED/REWARDED recovery, but it cannot make unrelated plugins transactionally atomic.

### Safety controls

```yaml
turn-in:
  allow-legacy-action: false
  prepare-timeout-seconds: 120
  minimum-free-slots: 1
  audit-log: true
  required-free-slots-by-quest: {}
```

Per-quest slot override example:

```yaml
turn-in:
  required-free-slots-by-quest:
    sunken_convoy: 3
```

Pending transactions are persisted in `pending-turnins.yml`. Audit entries are appended to `turnin-audit.log`.

The old `cdrjournal_turnin` action remains for compatibility but is disabled by default. Set `turn-in.allow-legacy-action: true` only if you intentionally accept the old immediate-commit behavior.

## Previous systems

- STORY / DAILY / LIMITED lifecycle.
- Admin-managed LIMITED windows.
- Persistent objective progress and timers.
- Protected player-bound written journals.
- Citizens NPC giver binding and `cdrjournal_correct_npc`.
- NPC-only accept/turn-in flow.
- BetonQuest 3.x custom actions/conditions.

## Build

Requirements: JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

Output: `target/CdrQuestJournal-0.4.0.jar`.

Target: Paper 1.21.11, BetonQuest 3.2.0, Citizens API 2.0.44-SNAPSHOT.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
