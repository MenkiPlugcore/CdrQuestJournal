# CdrQuestJournal

Quest Journal bridge for MoonSign S2. BetonQuest remains the quest engine/source of truth; CdrQuestJournal handles physical journals, lifecycle, Citizens quest-giver binding, safe turn-in transactions, crossplay-safe journal presentation, and admin management.

## v0.6.0 — Quest Admin GUI

Run `/cqj` or `/cqj gui` in-game with `cdrquestjournal.admin` to open the dashboard.

### Dashboard

- **Quest Manager** — browse STORY, DAILY, and LIMITED definitions.
- **Limited Quest Manager** — browse only LIMITED quests and manage live schedules.
- **Player Sessions** — inspect online players and restore active journals.
- **Reload** — reload plugin configuration, quest definitions, lifecycle data, and NPC bindings.

### Quest Detail

Each quest shows its type, ID, objectives, time limit, NPC binding, and lifecycle state.

NPC binding is GUI-driven: click **Quest Giver NPC**, close the inventory automatically, then right-click the Citizens NPC that should own the quest. The next Citizens click is persisted as the binding. Existing bindings can be removed from the same screen.

### Limited Quest controls

The GUI provides fast operational presets:

- Start now for 1 hour, 6 hours, or 24 hours.
- Extend current end time by 1 hour, 6 hours, or 24 hours.
- Clear schedule.
- View current start/end and ACTIVE / SCHEDULED / ENDED state.

Exact manual date/time scheduling remains available with:

```text
/cqj limited set <questId> <yyyy-MM-dd_HH:mm> <yyyy-MM-dd_HH:mm>
/cqj limited end <questId> <yyyy-MM-dd_HH:mm>
```

### Player session inspector

The GUI lists online players, their active journal count, active quest status/time, and provides an admin restore action. All gameplay-facing quest interaction remains NPC-based; this GUI is admin-only.

## Safe turn-in

Recommended BetonQuest flow remains:

```yaml
actions:
  prepare: cdrjournal_prepare lost_cargo
  reward_rep: cdrrep_add 25 "QUEST_COMPLETED"
  reward_items: give iron_ingot:3
  finalize: cdrjournal_finalize lost_cargo
  abort: cdrjournal_abort lost_cargo
```

## Existing systems

- STORY / DAILY / LIMITED lifecycle.
- Persistent objective progress and timers.
- Protected UUID-bound written journals.
- Citizens NPC giver binding.
- Two-phase safe turn-in and pending recovery.
- Crossplay-safe polished written-book UI.
- BetonQuest 3.x actions/conditions.

## Build

Requires JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

Output: `target/CdrQuestJournal-0.6.0.jar`.

Target: Paper 1.21.11, BetonQuest 3.2.0, Citizens API 2.0.44-SNAPSHOT.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
