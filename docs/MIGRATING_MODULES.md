# Migrating Plexon modules to PlexonCore 2

Migrate one repository at a time. Do not rewrite gameplay logic merely to adopt Core, and do not remove a module-local fallback until Core parity has been verified in production-like tests.

## Required sequence

1. Inspect the module's latest stable and active development branches.
2. Map every listener and classify it as high-frequency, medium-frequency, lifecycle or GUI/admin.
3. Resolve `PlexonCoreAPI` through Bukkit `ServicesManager`.
4. Register the module with an API 2 range such as `>=2.0 <3.0`.
5. Keep the existing API 1/standalone path available during the migration window where required.
6. For high-frequency block listeners, replace repeated player/world/material/origin/PDC resolution with a `CoreBlockBreakContext` subscription.
7. Subscribe only to materials the module actually consumes.
8. Request natural origin only when the module needs it.
9. Request only the PDC namespace(s) the module owns.
10. Keep cancellation/protection/current-drop decisions synchronous.
11. Send only immutable values to compute/IO workers.
12. Coalesce repetitive progression/persistence work instead of scheduling one task per event.
13. Publish module health through `ModuleRegistry`.
14. Compare the Core path and legacy path in equivalent runtime scenarios.
15. Profile with Spark before removing redundant module-local infrastructure.

## Origin migration rule

`BlockOrigin.UNKNOWN` must not be treated as natural for anti-exploit progression. During migration, keep the module's previous natural-block tracker available until the Core origin service has demonstrated parity across restart, chunk load/unload, placement, piston movement and destructive events.

## First pilot

The first Core 2 gameplay migration is **PlexonTools**. Its ordinary single-block mining listener should become the proof that shared context construction reduces repeated hot-path work without changing Legendary Tool progression or ability semantics.

PlexonTools continues to own tool definitions, progression, abilities, item visual updates and tool events. Core only supplies shared facts/runtime infrastructure.

## Acceptance before deleting the legacy path

- clean build and tests;
- plugin enable/disable/restart verified;
- no duplicate progression;
- no lost progression;
- no natural-block exploit;
- no unsafe async Bukkit access;
- bounded queues remain bounded under load;
- Spark comparison shows no material regression;
- rollback package remains available.
