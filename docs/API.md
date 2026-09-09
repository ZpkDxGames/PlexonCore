# PlexonCore API 2.0

PlexonCore 2.0 keeps the 1.0 services available while adding the shared high-frequency event/runtime APIs. The Core runtime advertises API 2.0 and temporarily accepts module ranges compatible with API 1.0 through the migration bridge.

## Obtain the API

```java
var registration = Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
if (registration == null) return;
PlexonCoreAPI core = registration.getProvider();
```

Use `core.supportsApi(2, 0)` or `core.supportsApi(1, 0)` when a module needs an explicit compatibility check.

## Register a module

Migrated modules should declare an API 2 range:

```java
core.modules().register(new ModuleRegistry.ModuleDescriptor(
    "tools",
    "PlexonTools",
    plugin.getName(),
    plugin.getPluginMeta().getVersion(),
    plugin,
    ModuleRegistry.ModuleVersionRange.parse(">=2.0 <3.0"),
    Set.of("legendary-tools", "block-break-consumer"),
    ModuleRegistry.ModuleState.READY,
    "Ready",
    Instant.now()
));
```

Existing modules that still declare `>=1.0 <2.0` remain accepted while the compatibility bridge is enabled. This bridge is transitional; new development should target API 2.

## Shared block-break gateway

Subscribe only to materials the module actually needs. Core precompiles these material routes, so an unrelated block does not trigger a global scan of every Plexon module.

```java
AutoCloseable blockSubscription = core.events().subscribeBlockBreak(
    "plexontools",
    CoreBlockSubscription.builder()
        .materials(Set.of(Material.STONE, Material.DEEPSLATE, Material.DIAMOND_ORE))
        .requiresNaturalOrigin(true)
        .requiresMainHandIdentity("plexontools")
        .build(),
    context -> {
        // context contains immutable player/world/block facts only.
        // Keep Bukkit mutation on the primary thread.
        // Tool progression and abilities remain PlexonTools-owned gameplay.
    }
);
```

Close the returned subscription when the module disables.

The gateway only inspects main-hand ItemMeta/PDC when at least one subscriber on that material requests identity data. Requested PDC namespaces are unioned once for the route.

## Immutable block context

`CoreBlockBreakContext` contains UUID/name/world coordinates/material, optional compact main-hand identity, block origin, game tick and creation timestamp. It intentionally does not retain `Player`, `World`, `Block`, inventory or Paper event references.

Do not move live Bukkit objects to async workers. If a module schedules compute/IO work, pass immutable values from the Core context.

## Block origin

```java
BlockOrigin origin = core.blockOrigins().origin(block);
```

Possible states:

- `NATURAL` — persisted chunk origin state is known and the position is not tracked as player placed.
- `PLAYER_PLACED` — the position is tracked as player placed.
- `UNKNOWN` — persisted chunk state is not loaded or origin persistence is unavailable.

Anti-exploit progression should fail closed on `UNKNOWN` rather than treating it as natural.

The service tracks placement, break/removal, explosions, burn/fade, piston movement and chunk load/unload. High-frequency lookups are memory-only; no SQLite query occurs from `BlockBreakEvent`.

## Compute and IO lanes

Legacy API 1 methods still work:

```java
core.scheduler().runAsync(() -> pureCompute());
core.scheduler().supplyAsync(() -> calculate());
```

API 2 additionally exposes a separate bounded IO lane:

```java
core.scheduler().runIo(() -> blockingIntegrationWork());
core.scheduler().supplyIo(() -> loadSerializedState());
```

Both lanes have bounded queues and rejection counters. Do not create one async task per high-frequency gameplay event; aggregate repetitive work instead.

## Batch accumulator

```java
BatchAccumulator<UUID, Integer> blocks = new BatchAccumulator<>(Integer::sum);
blocks.add(playerId, 1);
Map<UUID, Integer> batch = blocks.drain();
```

`drain()` swaps the active map atomically so new events are not lost while a consumer processes the previous batch.

## Text service

```java
Component safe = core.text().render(TextService.TextMode.SAFE, playerControlledValue);
Component legacy = core.text().render(TextService.TextMode.LEGACY, "&aLegacy");
Component mm = core.text().render(TextService.TextMode.MINIMESSAGE, "<green>Trusted admin template</green>");
```

`SAFE` is the default for untrusted runtime strings.

## GUI helpers

Use `core.gui().builder(...)` to create a protected inventory. GUI state stays module-owned while Core supplies protected session mechanics.

## Item snapshots

```java
ItemService.ItemSnapshot snapshot = core.items().snapshot(stack);
ItemStack restored = snapshot.restore();
boolean same = core.items().matches(a, b, ItemService.MatchMode.EXACT);
```

Exact snapshots remain appropriate for configuration/admin workflows. The high-frequency event gateway deliberately uses the smaller `CoreItemIdentity` instead of serializing full ItemStacks.

## SQLite

```java
SqliteService.SqliteDatabase db = core.persistence().open(pluginDataFolder.resolve("quests.db"));
db.executeWrite(connection -> {
    try (var ps = connection.prepareStatement("INSERT INTO example(value) VALUES (?)")) {
        ps.setString(1, value);
        ps.executeUpdate();
    }
});
```

Writes are serialized per database. Queries use the Core IO lane instead of the compute lane.

## Diagnostics and performance

`core.diagnostics()` keeps the established point-in-time health snapshot. API 2 runtime-specific data is available from:

```java
var eventMetrics = core.events().metrics();
var originStats = core.blockOrigins().stats();
```

`/plexon diagnostics` reports compute/IO queues, rejection counters, event/context/PDC/origin counts and origin-cache health. `/plexon perf` reports rolling gateway, context-build and dispatch P50/P95/P99 timings.
