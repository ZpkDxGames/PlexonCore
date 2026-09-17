# PlexonCore 2.1.0 Release Execution

## Release state

`RC SOURCE + CI + DOWNSTREAM CERTIFIED — LIVE RUNTIME CERTIFICATION PENDING`

Stable `2.1.0` publication remains blocked. The exact `2.1.0-rc.1` binary recorded below must still pass the production PlexonCraft runtime gate and the required soak before `main`, `release/stable`, the stable tag, or the stable GitHub Release may advance.

## Verified baseline

- Repository: `ZpkDxGames/PlexonCore`
- Baseline stable tag: `v2.0.5`
- Baseline `main` SHA: `f043828525c0c0e9afd4d3a5f8863082a8d08778`
- Baseline `release/stable` SHA: `f043828525c0c0e9afd4d3a5f8863082a8d08778`
- Baseline stable JAR: `PlexonCore-2.0.5.jar`
- Baseline stable JAR SHA-256: `bf4df796e83571e76c06b296c75c5e73ddc8053cb2c587d74c08cd3922e0b4d4`
- Target stable release: `2.1.0`
- Certified RC version: `2.1.0-rc.1`
- Paper target: `26.2.build.121-stable`
- Java target: `25` / class major `69`
- Baseline API: `2.0`, retaining the `1.0` compatibility bridge
- RC API: additive `2.1`, retaining API 2.0 surface and the API 1.0 compatibility bridge
- Open pull requests at reconnaissance: none
- `main` protection at reconnaissance and at RC closure: disabled

## Baseline verification

The unmodified baseline was verified by GitHub Actions run `34632587636` at the exact baseline SHA.

- `mvn -B -ntp clean verify`: PASS
- Tests: **26 run / 0 failures / 0 errors / 0 skipped**
- Java: Temurin `25.0.4+1`
- Distribution contract: PASS
- Paper/Bukkit/Adventure server APIs not shaded: PASS
- SQLite shading: PASS
- Embedded plugin version: PASS
- Java class major 69: PASS
- JAR SHA generation/check: PASS

## RC implementation closure

The hardening work is implemented on `agent/2.1.0-full-stable-hardening`.

Completed runtime/source areas:

1. **Event gateway** — corrected failure-log rate-limit timestamp semantics, isolated subscriber failures, owner-aware subscriptions, exact-owner cleanup, and disabled-owner fail-closed delivery.
2. **Player watch** — owner-aware registrations and cleanup plus coherent world-transition source metadata; when Bukkit cannot supply source coordinates Core records them as unknown instead of mixing the old world with destination coordinates.
3. **Block origin persistence** — in-memory authority preserved, last-write-wins coalescing buffer, batched/pressure flushes, retry visibility, bounded shutdown, and persistence-health telemetry.
4. **SQLite lifecycle** — one ordered lifecycle-owned writer connection per normalized DB path, database authority reuse, transaction rollback/recovery, future-schema refusal, and bounded close.
5. **Scheduler** — owner-scoped async/scheduled work, observable completion/failure, owner purge, repeating-task ownership, bounded shutdown, and contributor health. A regression test exposed a health-publication race; the returned future now becomes complete only after scheduler bookkeeping and health publication are committed.
6. **Health** — independent contributor-based Core health aggregation rather than one mutable status bit.
7. **Integrations** — capability/state dimensions refreshed on plugin lifecycle; active `PlexonGPFlags` identity retained and retired `PlexonCrates` is not treated as an active first-party dependency.
8. **GUI** — owner-aware session authority, exact-generation stale-session rejection, owner purge, safe click filtering, deferred-action revalidation, and immutable per-open callback routing.
9. **PlaceholderAPI bridge** — reflective method discovery moved out of rendering hot paths into a cached lifecycle adapter with explicit invalidation/refresh and failure observations.
10. **Item identity** — deterministic custom-ID conflict handling and stable fingerprint behavior.
11. **Event dedupe** — bounded expiry-driven memory dedupe without whole-map hot-path scans.
12. **Configuration** — pure validation policy, immutable generation publication, deprecated-key tolerance, explicit restart-required runtime sizing, and future-schema refusal.
13. **API** — additive API 2.1 exposure without removing the API 2.0 surface or API 1.0 compatibility bridge.

No PlexonPanel code was changed. No PlexonCrates revival was performed. Downstream repositories were compiled/tested but not modified.

## Exact RC evidence

The runtime candidate code and compatibility harness are pinned at:

- Certified candidate source SHA: `b259f6116ab26831ab250e0b61335908b87e71fa`
- Candidate version: `2.1.0-rc.1`
- Candidate JAR: `PlexonCore-2.1.0-rc.1.jar`
- Candidate JAR size: `12,244,403` bytes
- Candidate JAR SHA-256: `ae32e9722eca8fea2ccbe161e2748181349b6c2ec59f09962133094620293375`
- GitHub Actions artifact ID: `10518169162`
- Artifact ZIP SHA-256: `8cd61477bf8745d87146564dbd975d93b8517e3988ce107476f08b9badcea279`
- Core Build run: `35270431101` — **SUCCESS**
- Downstream Compatibility run: `35270431237` — **SUCCESS**

The JAR checksum above was verified both by CI's generated `SHA256SUMS.txt` and by an independent checksum of the downloaded Actions artifact.

Evidence-only documentation commits after `b259f6116ab26831ab250e0b61335908b87e71fa` do not alter the certified runtime source. If production code, resources, or `pom.xml` change after this point, the RC binary and all source/runtime gates must be regenerated from a new candidate SHA.

## Core CI gate

Exact-head Build run `35270431101` passed at candidate SHA `b259f6116ab26831ab250e0b61335908b87e71fa`.

- Production Java sources compiled: **27**
- Test Java sources compiled: **27**
- Tests: **66 run / 0 failures / 0 errors / 0 skipped**
- Maven build: PASS
- Distribution contract: PASS
- `plugin.yml` present and embedded version = `2.1.0-rc.1`: PASS
- PlexonCore main class present: PASS
- BlockOrigin runtime class present: PASS
- SQLite JDBC shaded: PASS
- Bukkit/Paper/Adventure server APIs excluded from shading: PASS
- Java class major `69`: PASS
- Paper pin `26.2.build.121-stable`: PASS
- generated JAR SHA-256 verification: PASS
- `git diff --check`: PASS

Regression coverage includes event failure rate limiting, owner-scoped event routing, player transition/lifecycle semantics, BlockOrigin coalescing, bounded dedupe, integration state, config policy, scheduler observability/cleanup/shutdown, GUI session generations/owner cleanup, PlaceholderAPI provider lifecycle, SQLite writer lifecycle/migration/recovery, health aggregation, API compatibility, and item identity.

## Downstream compatibility gate

Downstream Compatibility run `35270431237` completed **SUCCESS** at the exact candidate SHA.

The matrix built/tested the production set without committing or patching downstream repositories:

- PlexonUtility
- PlexonSpawners
- PlexonKeys
- PlexonChats
- PlexonQuests
- PlexonRanks
- PlexonShops
- PlexonSkills
- PlexonTools
- PlexonHomes
- PlexonTravel
- PlexonJobs
- PlexonBackpacks
- PlexonGPFlags

Result: **14/14 matrix jobs completed successfully.**

Compatibility method:

- The exact RC JAR bytes were installed into the CI runner under historical Maven Core coordinates used by downstream repositories.
- File-based Gradle consumers received the exact RC bytes only in their ephemeral `libs/PlexonCore-*.jar` path.
- Each downstream repository ran its own test build unchanged.
- Checkout-time tracked differences were captured as the baseline, and the compatibility build was required not to add any tracked-file mutation.

Harness issues encountered and resolved during certification were not product regressions:

- PlexonUtility's first compatibility run passed its 145 tests but the harness initially mistook untracked Maven `target/` output for a repository mutation.
- PlexonSkills expects `libs/PlexonCore-2.0.4.jar`; the corrected harness injected the exact RC JAR into that ephemeral path, after which its Gradle compile/test completed successfully.
- PlexonShops had checkout-time `gradlew.bat` line-ending normalization; the corrected harness preserved the checkout state as the integrity baseline, after which its Gradle compile/test completed successfully.

## Runtime gates still required

Stable publication is blocked until **the exact JAR SHA-256 recorded above** is tested on the real PlexonCraft runtime with:

- Paper 26.2
- Java 25
- upgrade from current 2.0.5 data
- clean Core startup and service registration
- API 2.1 / API 2.0 / API 1.0 bridge checks
- active integration discovery/capability checks
- owner lifecycle disable/re-enable checks
- player movement/world-change/teleport/disconnect checks
- block-origin rapid mutation, restart, persistence failure/retry, and shutdown-flush checks
- scheduler failure/recovery, cancellation, repeating-task, owner-cleanup, and shutdown checks
- GUI permission, stale-session, and lifecycle checks
- PlaceholderAPI disable/re-enable/provider-failure checks
- exact item-identity behavior for production custom items
- at least **30 minutes** of Spark/task/thread/heap/origin-queue observation
- no unresolved HIGH or CRITICAL defects

Current runtime gate status:

- Live PlexonCraft host connection: **UNAVAILABLE during this source/CI session**
- Exact-candidate runtime certification: **PENDING**
- 30-minute production-like soak: **PENDING**
- Spark/performance runtime result: **PENDING**
- Runtime-discovered HIGH/CRITICAL defects: **UNKNOWN until certification**

These gates must not be simulated or inferred from CI.

## Release engineering / governance state

- POM remains `2.1.0-rc.1`.
- No stable `v2.1.0` tag exists.
- No stable GitHub Release has been published.
- `main` remains at the accepted `v2.0.5` baseline until the runtime gate is closed.
- `release/stable` remains at the accepted `v2.0.5` baseline until the runtime gate is closed.
- Existing stable release workflow still requires stable-version semantics and exact `main`/`release/stable` SHA parity before publication.
- `main` is currently unprotected. Branch protection / required checks should be enabled before or as part of stable closure, but the current GitHub App connection does not expose repository-administration mutation needed to enforce that setting from this execution session.

## Stable closure sequence after runtime PASS

Only after the exact RC binary passes runtime certification and soak:

1. Record live-server evidence and performance/soak results in this ledger.
2. Confirm no production source/resource/POM change occurred after the certified RC. If any did, cut a new RC and repeat all gates.
3. Change project version from `2.1.0-rc.1` to `2.1.0` without altering certified runtime behavior.
4. Finalize `.release/2.1.0.md` from the prepared release notes.
5. Run the complete Core Build and Downstream Compatibility gates again for the stable-version commit.
6. Merge/advance `main` only with green checks.
7. Advance `release/stable` to the exact verified `main` commit so the stable release workflow can publish `v2.1.0`.
8. Verify published JAR, SHA-256, tag/source provenance, and rollback path.
9. Retain `v2.0.5` and its known-good JAR as the rollback baseline.

## Current decision

**Source/CI/downstream gate: PASS.**

**Stable release gate: BLOCKED on exact-candidate live runtime certification and the required 30-minute soak.**
