# PlexonCore API 1.0

## Obtain the API

```java
var registration = Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
if (registration == null) return;
PlexonCoreAPI core = registration.getProvider();
```

## Register a module

```java
core.modules().register(new ModuleRegistry.ModuleDescriptor(
    "quests",
    "PlexonQuests",
    plugin.getName(),
    plugin.getPluginMeta().getVersion(),
    plugin,
    ModuleRegistry.ModuleVersionRange.parse(">=1.0 <2.0"),
    Set.of("quest-engine"),
    ModuleRegistry.ModuleState.READY,
    "Ready",
    Instant.now()
));
```

The registry rejects incompatible Core API ranges without crashing the server.

## Text service

```java
Component safe = core.text().render(TextService.TextMode.SAFE, playerControlledValue);
Component legacy = core.text().render(TextService.TextMode.LEGACY, "&aLegacy");
Component mm = core.text().render(TextService.TextMode.MINIMESSAGE, "<green>Trusted admin template</green>");
```

`SAFE` is the default for untrusted runtime strings. Placeholder output is never treated as unrestricted MiniMessage by default.

## GUI helpers

Use `core.gui().builder(...)` to create a protected inventory. PlexonCore cancels item movement, drag operations and protected inventory transfer paths while a Core GUI holder is open. GUI state is tracked per player with module ID, GUI ID, page and open time.

## Item snapshots

```java
ItemService.ItemSnapshot snapshot = core.items().snapshot(stack);
ItemStack restored = snapshot.restore();
boolean same = core.items().matches(a, b, ItemService.MatchMode.EXACT);
```

Snapshots use Paper's raw ItemStack byte serialization, preserving supported metadata/data components rather than reducing an item to material/name/lore.

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

Each module should keep its own domain database. Core provides WAL, foreign keys, busy timeout, migration, backup and bounded writer infrastructure; it does not create one global database.

## Diagnostics and health

`core.diagnostics()` returns an immutable point-in-time snapshot. Modules can update their registry state/detail when degraded or failed so `/plexon diagnostics` exposes the condition without accessing private gameplay state.
