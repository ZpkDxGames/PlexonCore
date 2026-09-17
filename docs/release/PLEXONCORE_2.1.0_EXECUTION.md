# PlexonCore 2.1.0 Release Execution

## Release state

`FINAL STABLE RELEASE PIPELINE`

PlexonCore 2.1.0 uses a stable-only release policy. The 2.1 source line has completed the deep source audit and hardening cycle; prerelease identifiers are no longer used for publication. Final promotion requires the full-version Core build/distribution gate and the complete downstream compatibility matrix to pass.

## Verified baseline

- Repository: `ZpkDxGames/PlexonCore`
- Previous stable tag: `v2.0.5`
- Previous `main` SHA: `f043828525c0c0e9afd4d3a5f8863082a8d08778`
- Previous `release/stable` SHA: `f043828525c0c0e9afd4d3a5f8863082a8d08778`
- Previous stable JAR: `PlexonCore-2.0.5.jar`
- Previous stable JAR SHA-256: `bf4df796e83571e76c06b296c75c5e73ddc8053cb2c587d74c08cd3922e0b4d4`
- Target stable release: `2.1.0`
- Paper target: `26.2.build.121-stable`
- Java target: `25` / class major `69`
- Baseline API: `2.0`, retaining the `1.0` compatibility bridge
- Stable API: additive `2.1`, retaining API 2.0 and API 1.0 compatibility

## Deep source hardening completed

1. Event gateway failure isolation, rate limiting, disabled-owner fail-closed delivery, and exact owner cleanup.
2. Player-watch lifecycle ownership and coherent world-transition source metadata.
3. Block-origin coalesced last-write-wins persistence, batching, pressure flush, retry visibility, and bounded shutdown.
4. Lifecycle-owned SQLite writers, ordered writes, transaction rollback/recovery, future-schema refusal, and bounded close.
5. Scheduler observability, owner lifecycle, cancellation, repeating work, health publication ordering, and bounded shutdown.
6. Contributor-based Core health aggregation.
7. Capability/state-aware integration discovery and plugin lifecycle refresh.
8. GUI session authority, stale-generation rejection, safe click routing, deferred-action revalidation, and owner cleanup.
9. Cached PlaceholderAPI provider lifecycle outside rendering hot paths.
10. Deterministic custom-item identity conflict handling and stable fingerprints.
11. Bounded expiry-driven event dedupe without whole-map hot scans.
12. Immutable configuration publication, validation, deprecation tolerance, explicit restart-required settings, and future-schema refusal.
13. Additive API 2.1 contract while preserving API 2.0 and API 1.0 compatibility.
14. Expanded regression coverage across lifecycle, persistence, scheduler, GUI, integrations, configuration, identity, and compatibility paths.
15. Cross-repository compatibility workflow for the full production Plexon plugin set.

No PlexonPanel code was changed. PlexonCrates was not revived.

## Pre-final evidence

The hardened 2.1 implementation previously passed the source/build boundary before the stable version conversion:

- Hardened source SHA: `b259f6116ab26831ab250e0b61335908b87e71fa`
- Core Build run: `35270431101` — **SUCCESS**
- Tests: **66 run / 0 failures / 0 errors / 0 skipped**
- Downstream Compatibility run: `35270431237` — **SUCCESS**
- Downstream matrix: **14/14 successful**
- Documentation-sealed branch build: `35279930633` — **SUCCESS**

This evidence validates the hardened implementation. The final stable version commit is required to rerun both Core and downstream gates before promotion.

## Final stable build requirements

The 2.1.0 final commit must verify:

- Maven `clean verify`
- all regression tests
- exact `plugin.yml` version = `2.1.0`
- PlexonCore main class present
- BlockOrigin runtime class present
- SQLite JDBC shaded
- Bukkit/Paper/Adventure server APIs not shaded
- Java class major `69`
- Paper pin `26.2.build.121-stable`
- final JAR checksum generation and validation
- `git diff --check`

## Downstream compatibility requirements

The final `2.1.0` artifact must build/test successfully against:

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

Downstream repositories must remain unmodified.

## Runtime verification policy

Live-host certification remains strongly recommended and is documented in:

`docs/release/PLEXONCORE_2.1.0_RUNTIME_CERTIFICATION.md`

Under the stable-only policy it is no longer represented by a prerelease version. The checklist is used for deployment verification, performance observation, rollback validation, and post-release operational evidence.

A stable GitHub Release must never be described as live-certified unless that live evidence has actually been collected.

## Release engineering

- Project version: `2.1.0`
- Stable release notes: `.release/2.1.0.md`
- Stable workflow: `.github/workflows/release.yml`
- Release workflow requires stable-version semantics.
- Release workflow requires exact `main` / `release/stable` SHA parity.
- Release workflow rebuilds/verifies the distribution before publishing.
- Published assets: `PlexonCore-2.1.0.jar` and `SHA256SUMS.txt`.
- Previous `v2.0.5` remains the rollback baseline.

## Stable closure sequence

1. Commit the version conversion to `2.1.0`.
2. Require final-version Core Build success.
3. Require final-version Downstream Compatibility success across all 14 repositories.
4. Merge the verified stable source to `main`.
5. Confirm the `main` build is green.
6. Advance `release/stable` to the exact verified `main` SHA.
7. Allow the stable release workflow to build and publish `v2.1.0`.
8. Verify tag/source provenance, release assets, JAR checksum, and rollback availability.
9. Record live deployment/runtime evidence separately when the production host is upgraded.

## Current decision

**Release policy: STABLE ONLY.**

**Deep source audit/hardening: COMPLETE.**

**Final stable publication: gated only by the final-version automated build, downstream compatibility, branch parity, and release workflow.**
