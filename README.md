# PlexonCore

PlexonCore is the shared runtime foundation for the PlexonCraft plugin ecosystem. It centralizes cross-cutting infrastructure while keeping gameplay plugins independently versioned, replaceable and testable.

## 1.0.0 scope

- Core API and API-version compatibility (`1.0`)
- Plexon module registration and legacy discovery
- Atomic validated YAML configuration support
- Safe, legacy and MiniMessage text policies
- Controlled PlaceholderAPI rendering
- Lightweight protected GUI/session helpers
- Full Paper item snapshots and exact matching
- SQLite/WAL helpers with bounded asynchronous writes
- Integration registry
- Lifecycle-aware scheduler helpers
- Diagnostics, health snapshots and `/plexon`

PlexonCore does **not** own ranks, quests, crates, keys, shops, tools, chat, claims, backpacks, spawners, panel logic or blacksmith gameplay.

## Requirements

- Paper 26.2
- Java 25

## Installation

1. Download `PlexonCore-1.0.0.jar` from the GitHub Release.
2. Place it in `plugins/`.
3. Start the server.
4. Run `/plexon diagnostics` as an operator to inspect Core, module and integration health.

Existing Plexon plugins remain standalone until they explicitly adopt the Core API.

## API lookup

```java
RegisteredServiceProvider<PlexonCoreAPI> provider =
    Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
PlexonCoreAPI core = provider.getProvider();
```

See [`docs/API.md`](docs/API.md) for module registration, text, item, GUI, SQLite and diagnostics examples.

## Commands

- `/plexon` — ecosystem GUI for players with permission
- `/plexon status`
- `/plexon modules`
- `/plexon integrations`
- `/plexon diagnostics`
- `/plexon version`
- `/plexon reload` — reloads PlexonCore only

## Build

```bash
mvn -B -ntp clean verify
```

The release artifact is `target/PlexonCore-1.0.0.jar`.

## Compatibility and migration

PlexonCore plugin version `1.0.0` exposes Core API `1.0`. Modules should declare supported Core API ranges rather than matching an exact plugin build. Migration is intentionally incremental; see [`docs/MIGRATING_MODULES.md`](docs/MIGRATING_MODULES.md).
