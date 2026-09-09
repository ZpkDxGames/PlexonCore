# Plexon Ecosystem Certification Report

Phase 1 source/CI/release closure baseline: 2026-09-09.

This report is the final narrative companion to `PLEXON_ECOSYSTEM_BASELINE.md`, `PLEXON_CORE_COMPATIBILITY_MATRIX.md`, `PLEXON_API_CONTRACTS.md`, `PLEXON_ECOSYSTEM_DEFECT_LEDGER.md`, `PLEXON_PRODUCTION_DEPLOYMENT_MANIFEST.md`, and `plexon-ecosystem.lock.json`.

## 1. Certification scope

Phase 1 certifies repository source state, Core API/lifecycle compatibility, CI results, release provenance, exact release artifacts, cross-plugin ownership contracts, and the production deployment definition for the PlexonCore 2.0.4 baseline.

It does **not** certify live PlexonCraft runtime behavior. No authenticated server/SSH deployment channel was available for this closure.

## 2. PlexonCore baseline

The frozen baseline is:

- PlexonCore `2.0.4`
- release commit `83571edb428472649af87be9d52868fc8f325806`
- Core API `2.0`
- Paper `26.2.build.121-stable`
- Java `25`
- JAR `PlexonCore-2.0.4.jar`
- SHA-256 `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf`

Core 2.0.4 closed the lifecycle ownership defect by adding owner-aware module state mutation, owner-scoped unregister, and plugin-disable cleanup so a disabled or stale plugin instance cannot retain or mutate another instance's module registration.

## 3. Initial module-state problems

The original hard-stop set contained five modules observed/reported as stuck in `STARTING` rather than reaching their intended Core state:

- PlexonBlacksmith
- PlexonChats
- PlexonRanks
- PlexonBackpacks
- PlexonCrates

Initial count: **5**.

These were source/release problems, not runtime evidence fabricated from repository state.

## 4. Root cause of STARTING modules

The common root cause was an API-range/lifecycle mismatch in the stable-era Core bridges. The affected modules declared support ending before Core API 2 (`>=1.0 <2.0`). Core 2 could accept the compatibility registration path and expose a `STARTING` module, while each module's own compatibility guard rejected Core API 2 and therefore did not perform the normal READY/DEGRADED state transition.

The maintenance fixes expanded the bounded supported range through Core 2 and adopted owner-aware Core 2 lifecycle mutation/cleanup while preserving Core 1 compatibility where that release line still supported it.

## 5. Released maintenance fixes

All five original STARTING modules were source-fixed and released:

| Module | Final Phase 1 version |
|---|---:|
| PlexonBlacksmith | 1.4.1 |
| PlexonChats | 3.1.1 |
| PlexonRanks | 2.2.1 |
| PlexonBackpacks | 1.2.1 |
| PlexonCrates | 4.6.0 |

Exact commit, tag, JAR and SHA-256 values are authoritative in `PLEXON_CORE_COMPATIBILITY_MATRIX.md`, `PLEXON_PRODUCTION_DEPLOYMENT_MANIFEST.md`, and `plexon-ecosystem.lock.json`.

## 6. READY-module maintenance fixes

Four modules that were not part of the original STARTING count received additional maintenance/release work to close provenance, lifecycle, authority, or publication issues without reopening their product scope:

- PlexonKeys `1.4.1`
- PlexonQuests `3.3.1`
- PlexonShops `2.2.1`
- PlexonTools `4.2.1`

PlexonTools remains explicitly `LOCAL_AUTHORITY / CORE_REGISTERED`; the draft 4.3.0 Runtime migration is not part of this certified baseline.

## 7. Legacy Standalone remediation

The original Legacy/Standalone bucket contained **4** modules. All **4** were source-fixed or reclassified, and **4** active replacement/patch releases are represented by the final compatibility/deployment records. Runtime verification count remains **0**.

The remediation principle was to eliminate ambiguous duplicate authority: modules either register against the bounded Core contract, remain intentionally standalone-safe, or are explicitly replaced/deprecated rather than silently coexisting as competing implementations.

## 8. Source provenance resolution

Repository, branch/tag, release target, JAR name and SHA-256 provenance were traced for the active stable deployment set. Empty, young, historical, or renamed repositories were not treated as proof that a deployed binary was unmanaged.

Final unmanaged-binary count: **0**.

The machine-readable provenance baseline is `docs/ecosystem/plexon-ecosystem.lock.json`.

## 9. Deprecated / prerelease / planned repositories

The final classifications outside the active stable deployment set are:

- `PlexonClaimFlags` / `PlexonGriefPreventionAddon` — `DEPRECATED`; replaced by PlexonGPFlags. Historical 1.1.0 is rollback-only and must not be enabled alongside GPFlags.
- `PlexonSkills 1.0.0-rc.1` — `PRERELEASE_NOT_CERTIFIED`. Reported live behavior is not acceptable for stable promotion; Phase 2 treats Skills as the highest-priority major rebuild.
- `PlexonJobs 1.0.0-rc.1` — `PRERELEASE`. Stable promotion still requires real migration, SHADOW/PRIMARY, economy-rate, Spark, load, soak and rollback evidence.
- `PlexonUtility 1.0.0` — `PLANNED_NOT_ACTIVE`. A source release exists, but remaining Essentials ownership/decommission staging is unresolved.
- `Plexon-DailyRewards` — `DEPRECATED` for the Core 2.0.4 production baseline.

## 10. Cross-plugin double-processing audit

The source/API ownership audit found no known open HIGH/CRITICAL duplicate reward/progress path in the active stable deployment set.

Key authority results:

- PlexonTools uses local gameplay authority; Core is lifecycle/diagnostics only.
- PlexonQuests consumes Core-acquired facts without Core independently committing the same objective progress.
- PlexonKeys unregisters its local block acquisition listener in Core Runtime mode so the same block break is not processed by both paths.
- PlexonSkills and PlexonJobs are prerelease and excluded from active production reward processing.

Known duplicate reward/progress source defects open: **0**.

## 11. Vault / TheosisEconomy audit

TheosisEconomy remains the economy provider. Plexon modules integrate through Vault/provider-neutral adapters and do not replace the provider.

Reviewed transaction paths preserve abort/refund/compensation behavior where a charge can precede a later failure, including the relevant Shops, Homes, Travel, Blacksmith, Ranks and Crates flows. No certification change introduced a second economy provider or a known duplicate charge authority.

Known HIGH/CRITICAL economy source defects open: **0**.

## 12. Item custody / identity audit

The item-heavy modules were reviewed for namespaced identity, exact metadata custody, duplicate-drop prevention, GUI-close/inventory-full behavior, and persistence boundaries.

- Backpacks retain PDC-backed backpack UUID identity and close-time persistence.
- Blacksmith keeps the existing workstation transaction/rollback identity behavior; this wave did not redesign workstation mechanics.
- Crates retain exact-item snapshots and journal-first opening/recovery semantics.
- Keys retain the verified namespaced key identity/reward contracts.
- Tools retain existing PDC identity plus natural/player-placed provenance authority.
- Spawners require managed PDC/schema identity, fail closed on corrupt/unknown managed data, suppress simultaneous vanilla/managed drops, and drop inventory-full Essence leftovers rather than discarding them.

Known HIGH/CRITICAL item-loss, duplication, metadata-stripping, or inventory-full source defects open: **0**.

## 13. Known limitations

Phase 1 does not claim product perfection.

- PlexonSpawners `2.3.1`: `SOURCE STABILIZED`, `CORE 2.0.4 COMPATIBLE`, `PREMIUM/MAJOR REWORK REQUIRED`, `RUNTIME CERTIFICATION: NOT EXECUTED`.
- PlexonSkills `1.0.0-rc.1`: `PRERELEASE_NOT_CERTIFIED`; major functional rebuild is Phase 2 priority 1.
- PlexonJobs remains prerelease pending live migration/economy/performance validation.
- PlexonUtility remains inactive pending Essentials decommission/command-ownership validation.
- Panel 3.1.1 source/protocol compatibility is closed, but live relay/pairing end-to-end behavior for this maintenance artifact is not verified here.
- Live custom-item/provider combinations, command takeover, restart/reload/hot-enable behavior, Spark/MSPT and soak/load behavior remain runtime gates.

## 14. Runtime-certification boundary

`RUNTIME CERTIFICATION: NOT EXECUTED`.

This report does not claim:

- production/staging installation;
- observed Core module `READY` states;
- restart PASS;
- reload/hot-enable PASS;
- Spark/MSPT PASS;
- soak/load PASS;
- live GUI/custom-item PASS;
- live command-takeover PASS;
- live Panel relay/pairing PASS.

Those require the actual server environment.

## 15. Production deployment boundary

`PRODUCTION DEPLOYMENT: NOT EXECUTED`.

The deployment manifest defines the exact source/release-certified installable set and exclusions; it is not evidence that the JARs have been copied to PlexonCraft or started successfully.

## 16. Exact deployment manifest reference

Authoritative deployment definition:

- `docs/ecosystem/PLEXON_PRODUCTION_DEPLOYMENT_MANIFEST.md`
- `docs/ecosystem/plexon-ecosystem.lock.json`

The lockfile is the machine-readable source of exact plugin/version/commit/tag/JAR/SHA-256/Core-state/classification values. The manifest is the operator-facing deployment/exclusion guide.

Closure-release provenance additionally verified in this wave:

| Plugin | Version | Release commit | JAR SHA-256 |
|---|---:|---|---|
| PlexonTravel | 1.0.1 | `772188d55c694deea0bc5ae0bdce0898513499e5` | `39a9bdcce5a7796c3204872b7b8e42e91378b0d0508f74cc1151ab8f232bc252` |
| PlexonHomes | 1.0.1 | `d20bef0a76cd3006df51d46d67a3e48636650d31` | `fbc68fd1f705134ae728751ce91704f63ea89413af700156f6622b41a86a5096` |
| PlexonPanel | 3.1.1 | `e0984b625d692de6076afa7e20c4fe4b35f07e9a` | `ac96ad323602ca367ecfa6b2cddcd3f3a3251bb5f7f34c3195b740907c93ff40` |
| PlexonSpawners | 2.3.1 | `0ec54a04ecb77374874edf889b20286144c32a88` | `626299825e188db6f89dc5eb83f74bce3ce998aa3a45ffad817ee7372d39ffb8` |
| PlexonGPFlags | 1.0.1 | `757f62fa52fdb6ffa718c57ab4515b60c5aa23f3` | `fa7b60a81422d815ff777a46ce91dbfd5f877fcd39c1d5988dbc5ff67f19de82` |

## 17. Final certification counters

```text
Original STARTING modules:
  initial: 5
  source-fixed: 5
  released: 5
  runtime-verified: 0

Original Legacy Standalone modules:
  initial: 4
  source-fixed/reclassified: 4
  released active replacements/patches: 4
  runtime-verified: 0

Additional READY maintenance releases:
  Keys
  Quests
  Shops
  Tools
  count: 4

Unmanaged binaries:
  0

CRITICAL source defects open:
  0

HIGH source defects open:
  0

Runtime certification:
  NOT EXECUTED

Production deployment:
  NOT EXECUTED
```

## 18. Phase 1 declaration

When this documentation set is merged and read back from `PlexonCore/main`, Phase 1 is declared:

```text
SOURCE/CI/RELEASE CERTIFICATION COMPLETE
RUNTIME CERTIFICATION PENDING SERVER DEPLOYMENT
```

This declaration closes source/CI/release certification only. It does not convert any runtime or production deployment gate into PASS.

## 19. Phase 2 handoff

Phase 1 stops after the documentation merge/read-back. No premium-tier code changes are part of this closure.

The next program is `PREMIUM-TIER PRODUCT OVERHAUL`, using this report and lockfile as its baseline. Recommended order:

1. PlexonSkills — major functional rebuild
2. PlexonSpawners — major gameplay/GUI rebuild
3. PlexonCrates — premium editor/UX/mechanics
4. PlexonBlacksmith — premium workstation UX
5. PlexonBackpacks — premium inventory/storage UX
6. PlexonRanks — premium progression experience
7. PlexonQuests — premium quest journal/tracking
8. PlexonTools — premium progression/ability UX
9. PlexonShops — premium discovery/owner UX
10. remaining Plexon modules
