# CdrQuestJournal

`CdrQuestJournal` is the quest-journal layer for MoonSign S2. BetonQuest remains the quest engine; this plugin turns BetonQuest state changes into a protected written-book journal with live objective progress, countdown timers, and NPC turn-in flow.

## v0.1.0 — BetonQuest Journal Core

Implemented:

- Paper 1.21.11 / Java 21.
- Native BetonQuest 3.2.0 integration.
- Quest definitions in `quests.yml`.
- One bound written book per active quest.
- Objective progress with `add` and `set` modes.
- Automatic `READY` state when all journal objectives reach their targets.
- Real-time countdown refresh.
- Local expiration guard: expired journals cannot be turned in.
- NPC turn-in flow through BetonQuest conditions/actions.
- Journal removal after successful turn-in/fail/expire.
- Journal protection against dropping and common container transfers.
- Journal restoration after respawn/join.
- Persistent active-session state in `sessions.yml`.
- Admin-only diagnostics; players never need a command.
- Compatible with `CdrReputation` v0.2.0 through BetonQuest event chaining.

## Architecture

```text
Citizens NPC
    |
BetonQuest
    |
    +--> CdrQuestJournal
    |      - book
    |      - progress
    |      - timer
    |      - turn-in state
    |
    +--> CdrReputation
           - reputation reward / penalty
```

BetonQuest is the source of truth for quest logic and rewards. `CdrQuestJournal` is the presentation/turn-in layer. Reward text in `quests.yml` is display metadata; the actual reward should still be executed by BetonQuest.

## BetonQuest actions

```yaml
actions:
  journal_start: cdrjournal_start lost_cargo
  cargo_plus_one: cdrjournal_progress lost_cargo cargo add 1
  wreck_found: cdrjournal_progress lost_cargo find_wreck set 1
  journal_turnin: cdrjournal_turnin lost_cargo
  journal_fail: cdrjournal_fail lost_cargo
  journal_expire: cdrjournal_expire lost_cargo
```

The `amount` argument supports BetonQuest arguments/placeholders because it is parsed as a BetonQuest numeric argument.

## BetonQuest conditions

```yaml
conditions:
  journal_active: cdrjournal_active lost_cargo
  journal_ready: cdrjournal_ready lost_cargo
  journal_expired: cdrjournal_expired lost_cargo
```

A typical NPC completion branch checks `cdrjournal_ready lost_cargo`, then executes the turn-in action and the real reward actions. With `CdrReputation` v0.2.0:

```yaml
actions:
  rep_reward: cdrrep_add 25 "QUEST_COMPLETED"
```

For failed/expired quests, use the corresponding journal action and then a `cdrrep_remove` action.

## Quest definitions

`quests.yml` controls what the book displays:

```yaml
quests:
  lost_cargo:
    title: "Lost Cargo"
    type: "STORY"
    giver: "Harbor Master"
    time-limit-seconds: 2700
    objectives:
      cargo:
        text: "Ambil Lost Cargo"
        target: 12
    rewards:
      - "+25 Reputation"
      - "1x Treasure Map"
```

`time-limit-seconds: 0` disables the journal countdown.

## Admin commands

Permission: `cdrquestjournal.admin` (OP by default).

```text
/cqj reload
/cqj inspect <player>
/cqj restore <player> [questId]
```

There are intentionally no player-facing gameplay commands.

## Timer / expiration

The journal countdown is enforced locally for turn-in. BetonQuest should still own the actual quest-expiration event, for example with its delay/timer flow. When BetonQuest decides the quest has expired, execute:

```text
cdrjournal_expire <questId>
```

and then the reputation penalty / quest cleanup events. This avoids duplicate penalties and keeps BetonQuest as the quest-state authority.

## Persistence

Active journal sessions are persisted to:

```text
plugins/CdrQuestJournal/sessions.yml
```

Progress and expiration timestamps survive server restarts.

## Build

```bash
mvn clean package
```

Output:

```text
target/CdrQuestJournal-0.1.0.jar
```

## Next planned work

- Daily quest reset rules.
- Admin-managed limited quest windows.
- Better Citizens-specific visual feedback.
- Optional aggregated multi-quest journal mode.
- Richer reward/requirement rendering.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
