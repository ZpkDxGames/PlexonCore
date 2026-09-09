# Plexon Ecosystem Defect Ledger

Closure date: 2026-09-09.

## Closed certification defects

| ID | Severity | Area | Defect | Resolution |
|---|---|---|---|---|
| CORE-204-01 | HIGH | Core lifecycle | Disabled module owners could leave stale registrations; duplicate/late owners could mutate unsafe state paths. | PlexonCore 2.0.4 owner-aware state transitions, owner-scoped unregister and plugin-disable cleanup. |
| STARTING-01 | HIGH | Blacksmith/Chats/Ranks/Backpacks/Crates | Stable modules declared Core range ending before 2.0, leaving Core 2 registration stuck at `STARTING`. | Maintenance releases expanded supported range through Core 2 and adopted owner-aware lifecycle. |
| RELEASE-01 | MEDIUM | Crates | Publisher checksum was generated with `target/` path then verified from `target/`, producing an invalid path. | Publication-only repair; `v4.6.0` rebuilt from exact certified source commit. |
| RELEASE-02 | MEDIUM | Keys/Quests | One-shot publishers contained the same checksum-directory pattern. | Repaired before/with release publication; exact release targets verified. |
| TOOLS-01 | HIGH | Tools | Draft 4.3 Runtime migration had unresolved provenance/event-phase/performance ownership gates. | Draft was not promoted. Stable 4.2.1 uses `LOCAL_AUTHORITY / CORE_REGISTERED`; premium/runtime migration deferred. |
| TRAVEL-01 | HIGH | Travel | Managed source was pinned to Core 2.0.0 and used non-owner-scoped state mutation/unregister. | PlexonTravel 1.0.1, Core 2.0.4 pin, owner-aware state, owner-scoped cleanup, lifecycle tests. |
| HOMES-01 | HIGH | Homes | Build used a repository-local/system-scoped Core 2.0.3 dependency and old lifecycle mutation. | PlexonHomes 1.0.1 exact Core 2.0.4 provenance and owner-aware lifecycle. |
| PANEL-01 | HIGH | Panel | Paper agent declared Core 1.x-only compatibility and built against Core 1.0.0. | PlexonPanel 3.1.1 exact Core 2.0.4 lifecycle patch; protocol/security behavior unchanged. |
| SPAWNERS-01 | HIGH | Spawners | 2.3.0 compiled against Core 1.0.0, supported only Core 1.x, and had insufficient regression coverage. | PlexonSpawners 2.3.1 Core 2.0.4 stabilization + lifecycle/item/hot-path regression contracts. |
| GPFLAGS-01 | HIGH | GPFlags | Active replacement only detected Core by plugin name and had no module lifecycle registration/tests. | PlexonGPFlags 1.0.1 optional/reflection-isolated Core module, owner-aware lifecycle, persistence/identity tests. |
| GPFLAGS-02 | HIGH | Claim flags | Two overlapping Plexon claim-flags enforcement plugins could be deployed together. | PlexonGPFlags designated active replacement; old PlexonClaimFlags explicitly deprecated and excluded from deployment manifest. |
| PROV-01 | MEDIUM | Travel/planned repos | Empty/young repository assumptions could incorrectly classify managed binaries as unmanaged/planned. | Release/tag/source provenance traced explicitly; unmanaged count is zero for the audited named set. |

## Cross-plugin audit results

### Double processing

- PlexonTools: local authority only; Core lifecycle/diagnostics.
- PlexonQuests: Core fact acquisition does not independently commit objective progress.
- PlexonKeys: local block listener is unregistered in Core Runtime mode.
- PlexonSkills and PlexonJobs: prerelease, not production-certified, excluded from active reward processing.

Known duplicate reward/progress source defects open: **0**.

### Vault / TheosisEconomy

TheosisEconomy remains provider authority. Source review found compensating/refund behavior where an operation can fail after a charge and no certification patch introduces a duplicate provider/transaction path.

Known HIGH/CRITICAL economy source defects open: **0**.

### Item safety

Backpacks, Blacksmith, Crates, Keys, Tools and Spawners were reviewed for namespaced identity, exact metadata/custody, GUI-close/inventory-full handling and duplicate award paths. No known HIGH/CRITICAL source defect remains in the certified release set.

## Open items that are not source/release defects

The following are required runtime gates and remain `NOT EXECUTED` because no authenticated PlexonCraft server/SSH channel was available:

- actual server deployment;
- observed Core module `READY` states;
- startup/restart/reload/hot-enable cycles;
- Spark/MSPT comparisons;
- live inventory/custom-item/provider interaction;
- live command ownership/takeover;
- 30-minute soak/load tests;
- migration execution for prerelease Skills/Jobs;
- live Panel relay/pairing end-to-end checks for the 3.1.1 maintenance artifact.

They must not be represented as source defects or as passed tests.

## Final source severity counters

```text
CRITICAL open: 0
HIGH open:     0
```

Premium product quality work for Skills, Spawners, Crates, Blacksmith and Backpacks is the next phase and is not a blocker to this source/CI/release closure unless it exposes a new HIGH/CRITICAL correctness defect.
