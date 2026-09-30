# CdrQuestJournal

Quest Journal bridge for MoonSign S2. BetonQuest remains the quest engine/source of truth; CdrQuestJournal provides the physical journal, persistent progress display, lifecycle timers, protected quest books, and Citizens NPC turn-in flow.

## v0.3.0 — NPC Quest Turn-In & Quest Giver Binding

### Citizens NPC binding

Every quest can be bound to a specific Citizens NPC. With the default configuration, both accepting and turning in a quest must happen through that same NPC.

Admin flow:

```text
1. Look directly at the Citizens NPC.
2. /cqj npc bind <questId>
```

Other commands:

```text
/cqj npc info <questId>
/cqj npc unbind <questId>
```

Bindings are stored in `npc-bindings.yml` using Citizens NPC ID + UUID + display name.

### Secure interaction context

`NPCRightClickEvent` records a short-lived interaction context for the player. `cdrjournal_start` and `cdrjournal_turnin` validate that context before changing quest state. This prevents a quest from being accepted or claimed through a different NPC or unrelated trigger.

Default context lifetime: 60 seconds.

### BetonQuest

Actions remain:

```yaml
actions:
  accept: cdrjournal_start lost_cargo
  turnin: cdrjournal_turnin lost_cargo
  fail: cdrjournal_fail lost_cargo
  expire: cdrjournal_expire lost_cargo
```

New condition:

```yaml
conditions:
  correct_npc: cdrjournal_correct_npc lost_cargo
```

A typical NPC conversation can require both:

```text
cdrjournal_ready lost_cargo
cdrjournal_correct_npc lost_cargo
```

before executing `cdrjournal_turnin lost_cargo` and then the BetonQuest reward events such as CdrReputation changes and item rewards.

### Existing v0.2.0 lifecycle

- `STORY`, `DAILY`, and `LIMITED` quest types.
- Daily reset cycle with configurable timezone/reset time.
- Admin-managed limited quest start/end windows.
- Effective deadline uses the earliest player/lifecycle deadline.
- Daily and limited terminal outcomes persist across restart.

### Admin commands

```text
/cqj reload
/cqj inspect <player>
/cqj restore <player> [questId]
/cqj limited ...
/cqj npc ...
```

## Build

Requirements: JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

Output: `target/CdrQuestJournal-0.3.0.jar`.

Target: Paper 1.21.11, BetonQuest 3.2.0, Citizens API 2.0.44-SNAPSHOT.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
