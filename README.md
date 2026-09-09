# PlexonCore

PlexonCore is the shared runtime foundation for the PlexonCraft plugin ecosystem. It centralizes cross-cutting infrastructure while keeping gameplay plugins independently versioned, replaceable and testable.

## 2.0.0 scope

PlexonCore 2.0 introduces the Core-first high-frequency runtime described by the ecosystem modularization roadmap while preserving the services introduced in 1.0.

Core 2.0 foundation:

- Core API `2.0`
- compatibility bridge for API `1.0` module ranges during migration
- centralized block-break event gateway
- immutable shared block-break contexts
- precompiled material subscription routes
- lazy item/PDC identity inspection only when a subscriber requests it
- authoritative natural/player-placed/unknown block-origin service
- chunk-local origin caches backed by SQLite/WAL
- bounded compute and IO lanes with rejection metrics
- atomic batch accumulator for coalesced progression work
- runtime event, context, PDC, origin and failure counters
- rolling P50/P95/P99 Core stage timings
- `/plexon perf` and expanded diagnostics

Existing 1.0 services remain available: configuration, text, GUI/session helpers, exact item snapshots, integration lookup, SQLite helpers, module discovery and lifecycle utilities.

PlexonCore does **not** own ranks, quests, crates, keys, shops, tools, chat, backpacks, spawners or blacksmith gameplay. Gameplay semantics stay in independently versioned modules.

## Requirements

- Paper 26.2
- Java 25

## Installation

1. Download `PlexonCore-2.0.0.jar` from GitHub Releases.
2. Back up the existing `plugins/PlexonCore/` data directory when upgrading from 1.0.0.
3. Replace the old PlexonCore JAR in `plugins/`.
4. Start the server.
5. Run `/plexon diagnostics` and `/plexon perf` to inspect runtime health.

Existing Plexon gameplay plugins can remain installed while migration proceeds repository-by-repository through the API 1.0 compatibility bridge and legacy discovery.

## API lookup

```java
RegisteredServiceProvider<PlexonCoreAPI> provider =
    Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
PlexonCoreAPI core = provider.getProvider();
```

See [`docs/API.md`](docs/API.md) for API 2 event subscriptions, API 1 compatibility, scheduling, origin and diagnostics examples.

## Commands

- `/plexon` — ecosystem GUI
- `/plexon status`
- `/plexon modules`
- `/plexon integrations`
- `/plexon diagnostics`
- `/plexon perf` — Core gateway/context/dispatch latency percentiles
- `/plexon version`
- `/plexon reload` — reloads PlexonCore only

## Build

```bash
mvn -B -ntp clean verify
```

Stable artifact: `target/PlexonCore-2.0.0.jar`.

## Migration

Migration is repository-by-repository. Do not migrate every gameplay plugin in the Core 2 implementation itself. The first production pilot after the Core 2 backbone is PlexonTools because ordinary mining is the highest-value shared hot path. See [`docs/MIGRATING_MODULES.md`](docs/MIGRATING_MODULES.md).
