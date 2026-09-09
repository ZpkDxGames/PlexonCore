# Plexon Ecosystem API & Ownership Contracts

## 1. PlexonCore module lifecycle

All certified Core-aware modules must use a unique module ID, declare a bounded API range, register as `STARTING`, transition to `READY`/`DEGRADED`/`FAILED`, and clean up only registrations owned by the exact plugin instance.

Core 2 modules use owner-aware mutations:

- `updateState(moduleId, owner, state, detail)`
- `unregisterOwnedBy(owner)`

A late callback from an old/hot-disabled plugin instance must not mutate or remove a replacement module registration. Core 2.0.4 also removes module registrations when the owning plugin is disabled.

## 2. Core/local gameplay authority

A gameplay fact may be acquired by Core or locally, but a reward/progress mutation must have one authority path.

- **PlexonTools 4.2.1:** `LOCAL_AUTHORITY / CORE_REGISTERED`. Mining, progression, block provenance, abilities and SQLite remain local. Core is lifecycle/diagnostics only.
- **PlexonQuests 3.3.1:** Core block callbacks queue matching facts; Bukkit quest processing consumes the fact and commits quest progress once. Core does not independently award the same objective.
- **PlexonKeys 1.4.1:** local `BlockBreakEvent` acquisition is unregistered in Core Runtime mode. The same break is not processed by both Core and the local listener.
- **PlexonJobs 1.0.0-rc.1 / PlexonSkills 1.0.0-rc.1:** prerelease only; excluded from active production processing and therefore not allowed to become a second production reward authority during this certification phase.

## 3. Economy contract

TheosisEconomy remains the economy provider. Plexon modules consume the configured Vault service and do not replace TheosisEconomy.

Source-side transaction rules reviewed for the certified set:

- **PlexonShops:** warmup cancellation precedes charging; charge rejection aborts; asynchronous teleport failure refunds; visit/cooldown state is committed after success.
- **PlexonHomes:** final destination validation precedes charge; failed asynchronous teleport refunds.
- **PlexonTravel:** Vault access is provider-neutral/reflection-isolated; failed teleport paths preserve the existing refund mechanism.
- **PlexonBlacksmith:** Vault bridge exposes paired withdraw/refund operations and preserves the existing transactional workstation flow.
- **PlexonRanks:** money requirement consumption returns a compensating deposit action, allowing rollback when a later rank-up requirement/commit fails.
- **PlexonCrates:** Vault remains optional and crate reward/opening transaction semantics remain on the verified 4.6.0 line.

No certification patch introduces a second economy provider or duplicate known charge path.

## 4. Item identity and custody

Plugin-owned custom items must use plugin-namespaced identity/state and preserve exact metadata where identity matters.

- **Backpacks:** backpack UUID is stored in plugin PDC; inventory-close handling persists opened backpack state.
- **Blacksmith:** workstation processing preserves the existing item transaction/rollback semantics; the certification patch does not rewrite item identity.
- **Crates:** exact-item snapshots remain authoritative for custom-item rewards; opening correctness remains journal-first with recovery/inbox semantics.
- **Keys:** existing key identity/reward item contracts are preserved by the 1.4.1 lifecycle-only release.
- **Tools:** existing PDC item identity and natural/player-placed provenance are unchanged in 4.2.1.
- **Spawners:** managed items require `managed_spawner`, `spawner_type` and schema PDC; corrupt/unknown schema/type is rejected; break ownership disables vanilla duplicate drops; inventory-full Essence leftovers are dropped rather than discarded.

No HIGH/CRITICAL source defect involving known item duplication, GUI-close loss, metadata stripping or inventory-full destruction remained open at closure. Live inventory/plugin-interaction edge cases remain runtime validation.

## 5. GriefPrevention authority

`PlexonGPFlags` is the only active Plexon claim-flags/control plugin in the deployment manifest.

GriefPrevention remains authoritative for:

- claim ownership;
- boundaries;
- trust;
- subdivisions;
- claim blocks/overlap validation;
- persistent claim IDs.

GPFlags owns only its supplemental flag state, UI/actions and enforcement. The legacy `PlexonClaimFlags` API/flags import is a compatibility/migration surface, not a second enforcement plugin. `PlexonClaimFlags`/`PlexonGriefPreventionAddon` must not be enabled alongside GPFlags.

## 6. Panel protocol/security contract

PlexonPanel Paper/Host 3.1.1 keeps protocol version 3 and `/v1` compatibility with Dashboard/Relay 3.0.2.

Core lifecycle integration must not grant or modify:

- pairing identity;
- immutable device grants;
- relay authentication;
- action scopes/permissions;
- high-risk confirmation requirements;
- telemetry/privacy policy;
- console/chat stream authority;
- Host companion authority.

The Dashboard repository is a source/deployment component, not a Paper JAR artifact.

## 7. Runtime boundary

These are source/API contracts. Production behavior must still be confirmed on PlexonCraft. No document in this folder converts `NOT EXECUTED` runtime gates into a PASS.
