# CdrQuestJournal

Quest Journal bridge for MoonSign S2. BetonQuest remains the quest engine/source of truth; CdrQuestJournal provides the physical journal, persistent progress display, lifecycle timers, protected quest books, and NPC turn-in flow.

## v0.2.0 — Daily & Limited Quest Lifecycle

### Quest types

- `STORY` — normal quest lifecycle.
- `DAILY` — one terminal result per daily reset cycle. Completion, failure, and expiry all consume that day's attempt.
- `LIMITED` — available only inside an admin-managed event window. Completion, failure, and expiry lock the player for that event cycle.

Default lifecycle timezone is `Asia/Jakarta` and daily reset is `00:00`. Both are configurable in `config.yml`.

### Limited quest scheduling

A quest must use `type: LIMITED` in `quests.yml`. Admins then schedule its live window without editing BetonQuest files:

```text
/cqj limited set <questId> <yyyy-MM-dd_HH:mm> <yyyy-MM-dd_HH:mm>
/cqj limited end <questId> <yyyy-MM-dd_HH:mm>
/cqj limited now <questId> <durationHours>
/cqj limited info <questId>
/cqj limited clear <questId>
```

Example:

```text
/cqj limited set sunken_convoy 2026-09-30_18:00 2026-10-05_23:59
```

`limited end` can extend or shorten an active event while preserving the event cycle. Active journals automatically follow the updated end time.

### Effective quest deadline

The journal deadline is the earliest applicable deadline:

```text
min(player quest time limit, daily reset/event end)
```

If a quest has no per-player time limit, the daily reset or limited event end becomes the deadline.

### BetonQuest integration

Actions:

```yaml
actions:
  start_daily: cdrjournal_start fishermans_request
  start_limited: cdrjournal_start sunken_convoy
  progress_fish: cdrjournal_progress fishermans_request fish add 1
  turnin: cdrjournal_turnin fishermans_request
  fail: cdrjournal_fail fishermans_request
  expire: cdrjournal_expire fishermans_request
```

Conditions:

```yaml
conditions:
  daily_available: cdrjournal_available fishermans_request
  limited_available: cdrjournal_available sunken_convoy
  ready: cdrjournal_ready sunken_convoy
  active: cdrjournal_active sunken_convoy
  expired: cdrjournal_expired sunken_convoy
```

Use `cdrjournal_available` in NPC conversations before offering an accept option. It accounts for daily locks, limited start/end windows, previous terminal outcomes, and existing active sessions.

### Journal behavior

- Bound to the owner's UUID and quest ID.
- Objective progress updates in the written book.
- Countdown refreshes on the configured interval.
- Daily/limited availability information is shown in the journal.
- Completed objectives change the journal to `RETURN TO NPC`.
- Turn-in removes the book before BetonQuest grants rewards.
- Journal protection prevents normal dropping/storage abuse.
- Sessions persist across restart.

### Admin commands

Permission: `cdrquestjournal.admin` (default OP).

```text
/cqj reload
/cqj inspect <player>
/cqj restore <player> [questId]
/cqj limited ...
```

## Build

Requirements: JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

Output:

```text
target/CdrQuestJournal-0.2.0.jar
```

Target: Paper 1.21.11, BetonQuest 3.2.0.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
