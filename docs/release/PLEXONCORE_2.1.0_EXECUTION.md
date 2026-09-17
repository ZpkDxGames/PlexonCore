# PlexonCore 2.1.0 Release Execution

## Release state

`RUNTIME CANDIDATE`

Source, Core CI, distribution, and downstream compatibility gates are complete for `2.1.0-rc.1`. Stable `2.1.0` publication remains blocked until the exact candidate binary passes live PlexonCraft runtime certification and the required soak.

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

Completed source/runtime areas:

1. Event gateway failure isolation and owner lifecycle
2. Player-watch transition correctness and owner lifecycle
3. Block-origin coalesced persistence and writer lifecycle
4. SQLite writer lifecycle, transaction rollback/recovery, and future-schema refusal
5. Scheduler observability, owner lifecycle, cancellation, repeating work, and bounded shutdown
6. Contributor-based Core health aggregation
7. Capability-aware integration registry
8. GUI stale-session rejection, permission-safe routing, and owner lifecycle
9. Cached PlaceholderAPI adapter lifecycle
10. Deterministic item identity/fingerprint behavior
11. Bounded near-O(1) event dedupe
12. Immutable configuration generations with validation and restart-required semantics
13. Additive Core API 2.1 contract
14. Cross-repository compatibility matrix
15. Expanded regression tests

No PlexonPanel code was changed. PlexonCrates was not revived. Downstream repositories were compiled/tested but not modified.

## Exact RC evidence

- Certified candidate source SHA: `b259f6116ab26831ab250e0b61335908b87e71fa`
- Candidate version: `2.1.0-rc.1`
- Candidate JAR: `PlexonCore-2.1.0-rc.1.jar`
- Candidate JAR size: `12,244,403` bytes
- Candidate JAR SHA-256: `ae32e9722eca8fea2ccbe161e2748181349b6c2ec59f09962133094620293375`
- GitHub Actions artifact ID: `10518169162`
- Artifact ZIP SHA-256: `8cd61477bf8745d87146564dbd975d93b8517e3988ce107476f08b9badcea279`
- Core Build run: `35270431101` — **SUCCESS**
- Downstream Compatibility run: `35270431237` — **SUCCESS**
- Evidence-sealing documentation commit: `a412d22e855b700badc08064caa1943f11f7e0fd`

The candidate JAR checksum was verified both by CI's generated `SHA256SUMS.txt` and independently from the downloaded Actions artifact.

Documentation-only commits after the certified candidate do not alter the candidate runtime bytes. Any production source, resource, dependency, build configuration, or `pom.xml` change requires a new candidate and regeneration of all applicable gates.

## Core CI gate

Exact-candidate Build run `35270431101` passed at `b259f6116ab26831ab250e0b61335908b87e71fa`.

- Production Java sources: **27**
- Test Java sources: **27**
- Tests: **66 run / 0 failures / 0 errors / 0 skipped**
- Maven build: PASS
- Distribution contract: PASS
- `plugin.yml` and embedded `2.1.0-rc.1` version: PASS
- Core main class and BlockOrigin runtime class: PASS
- SQLite JDBC shaded: PASS
- Bukkit/Paper/Adventure server APIs excluded from shading: PASS
- Java class major `69`: PASS
- Paper pin `26.2.build.121-stable`: PASS
- generated JAR checksum verification: PASS
- `git diff --check`: PASS

## Downstream compatibility gate

Downstream Compatibility run `35270431237` completed **SUCCESS** for all 14 production-set repositories:

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

The exact RC bytes were supplied under each consumer's existing Maven/file-based dependency shape only inside ephemeral CI workspaces. Each repository ran its own build/tests unchanged, and the harness verified that compatibility work introduced no additional tracked-file mutation.

## Runtime certification gate

The repository now contains the exact-candidate checklist:

`docs/release/PLEXONCORE_2.1.0_RUNTIME_CERTIFICATION.md`

Required live gates include:

- Paper 26.2 / Java 25 identity
- exact candidate SHA-256 verification
- upgrade from the current 2.0.5 data set
- clean startup/service registration
- API 2.1 / 2.0 / 1.0 bridge validation
- integration discovery/capability validation
- owner disable/re-enable cleanup
- player transition checks
- event failure isolation
- GUI stale-session/permission/lifecycle checks
- PlaceholderAPI lifecycle
- block-origin rapid mutation/restart/retry/shutdown behavior
- SQLite migration/restart/transaction behavior
- scheduler failure/recovery/cancellation/repeating/shutdown behavior
- production custom-item identity
- diagnostics/health correctness
- at least **30 minutes** of Spark/task/thread/heap/queue observation
- clean shutdown/restart
- no unresolved HIGH or CRITICAL defect

Current runtime status:

- Exact-candidate deployment: **PENDING**
- Functional runtime checklist: **PENDING**
- 30-minute soak: **PENDING**
- Spark/performance evidence: **PENDING**
- Runtime defect classification: **PENDING**

These gates must not be inferred from CI.

## Release engineering / governance state

- POM remains `2.1.0-rc.1`.
- Stable release-note draft is prepared at `.release/2.1.0.md`.
- No stable `v2.1.0` tag exists.
- No stable GitHub Release has been published.
- `main` remains at the accepted `v2.0.5` baseline.
- `release/stable` remains at the accepted `v2.0.5` baseline.
- The stable release workflow runs only on `release/stable`, refuses prerelease version strings, requires exact `main`/`release/stable` SHA parity, requires `.release/<version>.md`, reruns build/distribution verification, and publishes the final JAR plus `SHA256SUMS.txt`.
- `main` remains unprotected. Branch-protection enforcement requires repository-administration capability not exposed by the current connected GitHub App.

## Stable closure sequence after runtime PASS

1. Record runtime evidence in this ledger.
2. Confirm the runtime-tested candidate bytes/source remain unchanged.
3. If any runtime code/build change is required, cut a new RC and repeat affected gates.
4. Change version from `2.1.0-rc.1` to `2.1.0` without changing certified runtime behavior.
5. Finalize `.release/2.1.0.md` if runtime findings require documentation.
6. Run complete Core Build and Downstream Compatibility gates at the stable-version commit.
7. Merge/advance `main` only with green checks.
8. Advance `release/stable` to the exact verified `main` SHA.
9. Verify the published `v2.1.0` tag, JAR, SHA-256, source provenance, and rollback path.
10. Retain `v2.0.5` and its known-good JAR as the rollback baseline.

## Current decision

**State: RUNTIME CANDIDATE.**

**Source/CI/downstream gate: PASS.**

**Stable release gate: BLOCKED on exact-candidate live runtime certification and the required 30-minute soak.**
