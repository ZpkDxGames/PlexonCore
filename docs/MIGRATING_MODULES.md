# Migrating Plexon modules to PlexonCore

Migrate one plugin at a time. Do not rewrite gameplay logic merely to adopt Core.

1. Add `softdepend: [PlexonCore]` while standalone operation remains supported.
2. Resolve `PlexonCoreAPI` through Bukkit `ServicesManager`.
3. Register a module descriptor and supported Core API range.
4. Replace duplicated integration detection with the Core integration registry.
5. Adopt the shared SAFE/LEGACY/MINIMESSAGE text policy.
6. Publish module health through the registry.
7. Adopt the configuration framework only where it reduces duplicated validation/migration logic.
8. Adopt GUI helpers where protected sessions/pagination are useful.
9. Adopt item snapshots/matching where exact custom-item preservation matters.
10. Adopt SQLite helpers where the module already owns persistent SQL data.
11. Keep domain state, commands and gameplay logic in the module.
12. Stage-test before switching from `softdepend` to `depend`.

Recommended first pilot after Core 1.0.0 is stable: PlexonQuests. That migration is intentionally outside the PlexonCore 1.0.0 release.
