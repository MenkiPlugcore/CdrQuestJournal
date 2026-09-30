# CdrQuestJournal

Quest Journal bridge for MoonSign S2. BetonQuest remains the quest engine/source of truth; CdrQuestJournal handles physical journals, lifecycle, Citizens quest-giver binding, safe turn-in transactions, and the player-facing quest book UI.

## v0.5.0 — Quest Journal UI Polish

The physical written book now receives a dedicated renderer instead of relying on the basic core pages.

### UI improvements

- Distinct `STORY`, `DAILY`, and `LIMITED` badges.
- Cleaner cover page with quest giver, status, and remaining time.
- Overall progress percentage and progress bar.
- Objective pages with completion markers and current/target values.
- Reward preview page.
- Failure/expiry consequence page.
- Daily reset / limited-event deadline page.
- Dedicated `RETURN TO NPC` completion page.
- More readable item display name + lore in inventory.
- Fresh rendering on the journal refresh cycle and immediately when the player interacts with the book.

### Crossplay-safe mode

Default configuration uses ASCII-safe symbols so Java and Bedrock do not depend on hover text, clickable components, or uncommon glyphs.

```yaml
journal-ui:
  enabled: true
  crossplay-safe: true
  progress-bar-width: 12
  objectives-per-page: 4
  show-accepted-at: true
  show-cycle-info: true
  item-name: "&6&lQuest Journal &8• &f%quest%"
```

With `crossplay-safe: true`, progress and objectives use forms such as:

```text
[######------] 50%
[x] Find the wreck
[ ] Recover cargo
```

Setting it to `false` enables the more decorative square/check symbols.

## Safe turn-in flow

The v0.4.0 transaction model remains unchanged:

```text
NPC click
  -> cdrjournal_prepare
  -> BetonQuest reward actions
  -> cdrjournal_finalize
  -> journal removed
  -> quest committed
```

Pending transactions remain persisted in `pending-turnins.yml`, with recovery and audit logging.

## Existing systems

- STORY / DAILY / LIMITED lifecycle.
- Admin-managed LIMITED windows.
- Persistent objective progress and timers.
- Protected player-bound written journals.
- Citizens NPC giver binding and `cdrjournal_correct_npc`.
- NPC-only accept/turn-in flow.
- Safe PREPARED / REWARDED / COMMITTED turn-in transactions.
- BetonQuest 3.x custom actions/conditions.

## Build

Requirements: JDK 21 and Maven 3.9+.

```bash
mvn clean package
```

Output: `target/CdrQuestJournal-0.5.0.jar`.

Target: Paper 1.21.11, BetonQuest 3.2.0, Citizens API 2.0.44-SNAPSHOT.

## License

MENKIESTES SOFTWARE LICENSE v1.0. See `LICENSE`.
