# Changelog

All notable changes to CdrQuestJournal are documented here.

## 1.0.0 — Production Release

- Declares the current per-player quest architecture production-stable.
- Freezes core gameplay behavior introduced across the 0.x series.
- Keeps BetonQuest as the quest engine/source of truth.
- Keeps Citizens as the NPC quest-giver layer.
- Keeps CdrReputation integration optional through BetonQuest actions/conditions.
- Retains safe two-phase turn-in transaction recovery.
- Retains STORY / DAILY / LIMITED lifecycle handling.
- Retains story chains, persistent progression, history, abandon, and cooldown.
- Retains crossplay-safe written-journal presentation and Geyser fallback.
- Retains config migration, startup diagnostics, duplicate/orphan journal sanitation, and orphan session preservation.
- Production CI now runs Maven `verify` and validates critical files/classes inside the generated JAR.
- Plugin metadata was generalized for reuse outside a single server deployment.

## 0.9.5 — Reliability, Crossplay & Migration Polish

- Added config schema migration with timestamped backups.
- Added startup diagnostics.
- Added duplicate/orphan physical journal sanitation.
- Added conservative orphan-session preservation.
- Added circular STORY dependency detection.
- Added Geyser crossplay-safe fallback.

## 0.9.0 — Quest History, Abandon & Cooldown

- Added persistent per-player quest history.
- Added `COMPLETED`, `FAILED`, `EXPIRED`, and `ABANDONED` outcomes.
- Added NPC-only abandon flow.
- Added generic quest cooldowns derived from persistent history.
- Added history/cooldown inspection to the admin GUI.

## 0.7.0 — Quest Chain & Story Progression

- Added persistent STORY completion.
- Added `requires-all` and `requires-any` prerequisites.
- Added BetonQuest story-completion condition.
- Story completion is only recorded after safe turn-in COMMIT.

## 0.6.0 — Quest Admin GUI

- Added admin dashboard, quest browser, limited scheduler controls, player session inspector, and GUI-driven Citizens NPC binding.

## 0.5.0 — Quest Journal UI Polish

- Added crossplay-safe polished written-book layout, progress display, lifecycle information, reward/failure previews, and return-to-NPC presentation.

## 0.4.0 — Safe Turn-In & Reward Delivery

- Added persistent PREPARED -> REWARDED -> COMMITTED turn-in transaction flow.
- Added pending recovery, anti-double-claim locking, inventory-space checks, and audit logging.

## 0.3.0 — NPC Quest Turn-In & Quest Giver Binding

- Added Citizens NPC binding by ID/UUID.
- Added strict start/turn-in validation against the bound quest giver.

## 0.2.0 — Daily & Limited Quest Lifecycle

- Added DAILY reset lifecycle.
- Added admin-managed LIMITED quest scheduling.
- Added availability conditions and persistent lifecycle locks.

## 0.1.0 — Quest Journal Core

- Added physical UUID-bound written journals.
- Added persistent sessions, objective progress, timers, journal protection, and native BetonQuest 3.x integration.
