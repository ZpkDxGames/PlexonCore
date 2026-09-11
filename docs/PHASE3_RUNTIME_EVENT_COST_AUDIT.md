# Phase 3 Runtime Performance & Event Architecture — Audit Checkpoint

Pipeline: `01_Runtime_Performance_Event_Architecture`

Status: **AUDIT COMPLETE / REFACTOR GATE OPEN**

This document is the mandatory evidence checkpoint before Pipeline 1 implementation. It audits the frozen/current Phase 2 candidates rather than stale default branches.

## Audit bases

| Repository | Audited ref | Write status |
|---|---|---|
| PlexonCore | `3148380db0abaa95325e275d3d0c9e4e80e4bcd4` (`agent/2.0.4-core-certification`) | Pipeline 1 write |
| PlexonTools | `f43475cb825f7e52e1a7c7be096df4e7c211dcff` (`phase2/4.3.0-runtime-performance-hardening`) | Pipeline 1 write |
| PlexonSkills | `1b25b5021662a0942407894c956157a96a996d7a` (`agent/2.0.0-premium-tier-product-rebuild`) | Pipeline 1 write |
| PlexonJobs | `4929145d2e594fef5714319b8f668076fc66498a` (`phase2/1.0.0-runtime-correctness-hardening`) | Pipeline 1 write |
| PlexonQuests | `3f04e2823c2ee53b67387846333ffb312cc0313a` (`phase2/4.0.0-premium-journal`) | Pipeline 1 write — runtime/event/service only |
| PlexonKeys | `4708ce831cc8c577696f7c8fd48df322e7ccd5a3` (`phase2/2.0.0-premium-key-provenance`) | **read only** |
| PlexonSpawners | `be65cb636bf21f6aeef6a0f1d87c470882df472d` (`phase2/3.0.0-premium-rebuild`) | **read only** |

PlexonCrates, PlexonUtility, PlexonTravel, PlexonHomes, and PlexonChats remain inspection-only for this pipeline and are not implementation targets.

## Hot event inventory / cost model

The risk rating is about synchronous frequency/cost, not listener count.

| Plugin | Event / acquisition | Priority | ignoreCancelled | Main-thread work | PDC / provenance | Protection lookup | DB | Vault | Tasks | Risk |
|---|---|---:|---:|---|---|---|---|---|---|---|
| Core | `BlockBreakEvent` acquisition | HIGHEST | no | one precompiled material route; capture immutable coordinates/player/final candidates | resolves block origin once only when any subscriber requests it; main-hand PDC only for requested namespaces | no direct WG/GP call; cancellation remains Bukkit-authoritative | none | none | none | low |
| Core | `BlockBreakEvent` commit | MONITOR | checked explicitly | dispatch immutable context once to interested subscribers | no second origin/PDC read | cancelled events never dispatch | none | none | none | low |
| Core | block provenance lifecycle (`Place`, `Break`, burn/fade/explosion/piston, chunk lifecycle) | MONITOR/lifecycle | mixed | in-memory chunk/position updates | authoritative `NATURAL` / `PLAYER_PLACED` / `UNKNOWN`, fail-closed while unresolved | n/a | persistence is asynchronous/coalesced | none | chunk-load/persistence work is bounded, not task-per-break | medium |
| Core | player watch gateway (`Move`, damage, quit, world, death) | MONITOR | mixed | one UUID map lookup and immediate return when no watch | none | event cancellation respected where applicable | none | none | none per signal | low |
| Core | Core GUI `InventoryClick`/`InventoryDrag` | normal | true | holder/session routing | item metadata only for GUI semantics | n/a | none hot-path | none | none | low |
| Tools | `BlockBreakEvent` tool decision | HIGH | true | active-context lookup; latest-state lookup; permission/world validation; XP-drop ability adjustment | compact tool identity PDC revalidated at most once/10 ticks per active player, plus cache-miss resolution | no direct WG/GP call | no sync DB | none | none | **high** |
| Tools | `BlockBreakEvent` tool commit | MONITOR | explicit | repeats active-context lookup, world/owner validation and latest-state lookup; target route; progression; abilities | **separate local provenance consume** in addition to Core provenance | relies on final Bukkit cancellation | registry mutation is in-memory/dirty-queued | none | no per-break task | **high** |
| Tools | `NaturalBlockTracker` lifecycle | MONITOR/lifecycle | mixed | duplicate placed-block provenance cache maintained beside Core | own chunk cache + `UNKNOWN` fail-closed | n/a | async registry chunk loads/writes | none | tasks only on chunk-load batches/retry, not steady-state block breaks | high |
| Tools | `PlayerInteractEvent` | HIGH | true | local tool inspect, definition/use checks, ability routing | local item PDC inspect | no broad direct WG/GP query | none hot-path | none | none | medium |
| Tools | entity death/damage, fishing/harvest, inventory/item lifecycle | HIGH/MONITOR/mixed | mixed | tool eligibility and progression-specific work | local item identity where applicable | Bukkit cancellation semantics | dirty/coalesced persistence | none | fixed flush tasks only | medium |
| Tools | `BlockDropItemEvent` / passive ability state | HIGHEST/MONITOR | mixed | bounded drop correlation / holder reconciliation | local tool state | n/a | none hot-path | none | none per event | medium |
| Skills | Core block subscriber | Core MONITOR dispatch | n/a | precompiled skill route; policy; player/profile lookup; XP mutation/custom event | **handler consumes `context.origin()` but subscription did not request origin** | Core final cancellation | in-memory dirty profile only | none | none | **medium correctness + perf coupling** |
| Skills | creature spawn/death, combat, fish, fall, inventory/anvil/brew | HIGHEST/MONITOR | mixed | bounded attribution/cooldown maps and progression | bounded entity-origin map where needed | Bukkit cancellation | profile persistence is async batched | none | none per event | medium |
| Jobs | Core block subscriber | Core MONITOR dispatch | n/a | precompiled material->job route; player/profile/limit arithmetic; custom payout/XP events | consumes shared Core origin; requests origin | Core final cancellation | profile/shadow writes on fixed flush cadence | **coalesced per-player payout flush**, not per block | two fixed repeating tasks | medium |
| Quests | Core block subscriber | Core MONITOR dispatch | n/a | maps Core context into `BlockFact` | shared Core origin when authoritative | Core final cancellation | none at acquisition | none | none | medium |
| Quests | **second** `BlockBreakEvent` listener | MONITOR | explicit | thread-local fact correlation, material/quest routing, objective processing | uses Core fact when authoritative; local provenance fallback otherwise | final cancellation | progress persistence is service-managed | none | none | **high duplicate dispatch/correlation** |
| Quests | local provenance lifecycle | MONITOR/lifecycle | mixed | compatibility/shadow provenance | required for LOCAL/SHADOW and migration; should not be deleted blindly | n/a | persistence outside committed event path | none | bounded lifecycle work | medium |
| Quests | entity/fish/craft/smelt/enchant/brew/world/advancement objective events | MONITOR/mixed | mixed | early `interested` checks then objective processor | entity spawn-reason PDC only when objectives require it | Bukkit cancellation | no direct hot-event DB query observed | none | no task/event | medium |
| Quests | GUI/presentation listeners | mixed | mixed | audited only | presentation metadata as required | n/a | n/a | n/a | n/a | out of Pipeline 1 write scope |
| Keys | Core block subscriber | Core MONITOR dispatch | n/a | classify material + eligibility/reward attempt | requests shared Core natural origin | Core final cancellation | bounded/coalescing single DB worker | none in mining path | no task/block | low; **read only** |
| Spawners | break/place/chunk/provenance listeners | mixed | mixed | managed-spawner identity/state/provenance | independent spawner provenance domain | Bukkit semantics + plugin-specific checks | plugin persistence | none observed in audited path | lifecycle tasks as designed | defer to P4; **read only** |

## Single ordinary block break — BEFORE

Assume an ordinary successful natural block, a valid active Legendary Tool, and Skills + Jobs + Quests + Keys routes that are interested in that material.

1. Bukkit creates one `BlockBreakEvent`.
2. Tools `HIGH`:
   - active player/context lookup;
   - latest registry state lookup;
   - owner/world/level validation;
   - compact PDC identity revalidation at most once per 10 game ticks for a stable active tool (full identity resolution on miss/change);
   - block XP ability decision.
3. Core `HIGHEST`:
   - exactly one precompiled material route-plan lookup;
   - exactly one Core block-origin lookup because Jobs/Quests/Keys request natural origin;
   - zero Core item-PDC scans today because no current block subscriber requests an item namespace;
   - one immutable `CoreBlockBreakContext` allocation plus one pending-event entry.
4. Core `MONITOR`, only if not cancelled:
   - one subscriber traversal;
   - Skills route/policy/profile work;
   - Jobs route/profile/limit/reward arithmetic and in-memory payout accrual;
   - Quests allocates/maps a `BlockFact` and pushes it into a thread-local correlation deque;
   - Keys maps a `BlockFact`, classifies the material and runs in-memory eligibility/reward logic.
5. Tools `MONITOR`:
   - second active-context lookup;
   - second owner/world/level validation;
   - second latest-state lookup;
   - target route;
   - **second, Tools-owned provenance lookup/consume**;
   - in-memory progression mutation + dirty/coalesced registry update;
   - coalesced visual/progress-event work.
6. Quests `MONITOR` listener:
   - **second Bukkit listener hop for the same committed break**;
   - thread-local correlation search/consume;
   - objective route/progress processing.
7. Core and local provenance cleanup observes/removes the broken position later in MONITOR ordering as applicable.

### Count summary — BEFORE

| Operation | Ordinary natural break |
|---|---:|
| Bukkit `BlockBreakEvent` instances | 1 (Area Mine secondary fan-out is disabled) |
| Core material route-plan lookup | 1 |
| Core provenance resolution | 1 when any interested subscriber requires origin |
| Tools provenance resolution/consume | 1 additional for a tracked Legendary Tool break |
| Explicit Plexon WorldGuard/GriefPrevention API lookups | 0 |
| Tools HIGH tool validation | 1 |
| Tools MONITOR repeated validation | 1 additional |
| Tools latest-state lookups | up to 2 across HIGH/MONITOR |
| Tools compact item PDC identity resolutions | cache miss/change, otherwise amortized once per 10 game ticks |
| Skills eligibility pass | 1 if material routed |
| Jobs eligibility pass | 1 if material routed |
| Quests Core acquisition pass | 1 if material routed |
| Quests second correlation/listener pass | 1 additional |
| Keys eligibility pass | 1 if material routed |
| synchronous DB query/write | 0 in audited steady-state ordinary break |
| Vault deposit/withdraw | 0 per break; Jobs accrues and flushes later |
| scheduler task creation | 0 per steady-state ordinary break |
| repeated YAML serialization | 0 |
| recursive/synthetic secondary `BlockBreakEvent` | 0; Area Mine is fail-closed |

## Duplicated or unsafe work found

### Duplicated

- PlexonTools maintains and queries a second natural/player-placed provenance cache even while Core already resolves the same block origin for downstream modules.
- PlexonTools repeats `fastCanUse` and `latestState` between its HIGH decision stage and MONITOR commit stage for the same event.
- PlexonQuests receives a Core `BlockFact`, stores it in a thread-local deque, then waits for its own MONITOR `BlockBreakEvent` listener to correlate and process the same break.
- Multiple progression modules necessarily perform their own business eligibility checks. Those checks are **not** duplicates when they depend on plugin-specific policy and must remain local.

### Hot synchronous work

- Tools HIGH + MONITOR validation/state/provenance path is the highest-value single-block target.
- Quests' duplicate committed-event hop is avoidable overhead.
- Skills' per-block custom XP event is synchronous but public plugin semantics; do not batch/remove without an explicit API decision.
- Jobs' per-eligible-job custom payout/XP events are synchronous semantic hooks; persistence/payout itself is already coalesced.

### DB / Vault / tasks

No audited steady-state ordinary block path performs a direct synchronous SQLite query/write, a Vault transaction, or scheduler task creation per break. Existing persistence systems use dirty snapshots, bounded/coalesced workers, or fixed periodic flushes. Tools provenance may schedule an asynchronous chunk-load batch when entering unresolved chunks; that is a cache lifecycle miss, not one task per ordinary break.

## Protection / cancellation model

No direct Plexon WorldGuard or GriefPrevention query is required for an ordinary single block break. Protection plugins remain authoritative by cancelling the original Bukkit event. Core dispatches committed subscribers only after the event survives cancellation, and Tools' MONITOR progression checks final cancellation.

Pipeline 1 must not replace mutable Bukkit cancellation semantics with post-commit-only routing. Tools' mutation-sensitive HIGH behavior remains plugin-local. Area Mine remains `DISABLED_SAFE`; Phase 2 already removed recursive synthetic `BlockBreakEvent` fan-out.

## What remains plugin-local

- Tools mutable decision/ability behavior that must run before event commit.
- Tools tool definition/state/progression policy and item rendering.
- Skills skill policy, XP formula, anti-exploit rules, and public XP events.
- Jobs job membership, daily caps, reward formulas, and payout ledger.
- Quests objective semantics plus LOCAL/SHADOW provenance fallback until migration is authoritative.
- Keys reward probability/cap/cooldown rules.
- Spawner origin/managed-spawner semantics until Pipeline 4 releases write ownership.

## What should be shared

- Core remains the owner of immutable committed block context, material subscriber routing, and the authoritative natural/player-placed origin for Core-native consumers.
- Interested modules should request only facts they require through `CoreBlockSubscription`.
- A Core subscriber should process its committed fact directly where no mutable Bukkit semantics are required; it should not re-correlate the same fact through another Bukkit listener.

## Safe caching/coalescing opportunities

1. Reuse Tools' exact HIGH-stage resolved context at MONITOR for the same `BlockBreakEvent`, instead of re-reading active state/validation twice.
2. Make Skills explicitly request Core provenance; this removes accidental dependence on another module requesting it.
3. Process Quests Core-authoritative block facts directly from the Core committed callback and bypass the second Bukkit correlation path; preserve LOCAL/SHADOW fallback.
4. Keep Jobs payout accrual and dirty-profile persistence as-is unless profiling identifies a measurable downstream bottleneck.
5. Do not migrate Tools provenance into Core in the first refactor without a compatibility/import proof; Tools' existing placed-block database is production data and UNKNOWN must continue to fail closed.

## Implementation gate decision

The audit supports targeted changes rather than listener-count reduction for its own sake. The first implementation tranche will therefore:

- optimize Tools HIGH -> MONITOR event context reuse without changing cancellation authority;
- fix Skills' provenance subscription contract;
- remove Quests' Core-authoritative double-dispatch/correlation while retaining local/shadow semantics;
- add regression/contract tests for one-route-per-event, cancelled/no-commit behavior, UNKNOWN fail-closed behavior, and no task-per-event assumptions where the existing test harness permits;
- leave Core's existing two-phase gateway architecture intact unless tests expose an API gap.

No stable tag or main-branch merge is authorized by this pipeline.