# PlexonCore 2.1.0 Release Execution

## Release state

`FINAL STABLE — AUTOMATED GATES PASSED`

PlexonCore now follows a stable-only release policy. The deep source audit/hardening cycle is complete, the project version is `2.1.0`, the final Core build/distribution gate is green, and the full 14-plugin downstream compatibility matrix is green.

## Baseline and target

- Repository: `ZpkDxGames/PlexonCore`
- Previous stable tag: `v2.0.5`
- Previous stable SHA: `f043828525c0c0e9afd4d3a5f8863082a8d08778`
- Previous stable JAR SHA-256: `bf4df796e83571e76c06b296c75c5e73ddc8053cb2c587d74c08cd3922e0b4d4`
- Final release: `2.1.0`
- Paper target: `26.2.build.121-stable`
- Java target: `25` / class major `69`
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
14. Expanded lifecycle, persistence, scheduler, GUI, integration, configuration, identity, and compatibility regression coverage.
15. Cross-repository compatibility workflow for the production Plexon plugin set.

No PlexonPanel code was changed. PlexonCrates was not revived.

## Final stable artifact evidence

- Audited/hardened runtime source SHA: `b259f6116ab26831ab250e0b61335908b87e71fa`
- Final stable conversion source SHA: `f13b94210febf5ea62d03be89081d1750b49cbe9`
- Difference after hardened source: release docs plus one POM version change from `2.1.0-rc.1` to `2.1.0`; no production Java/resource behavior changes
- Branch-head Build run: `35285439890` — **SUCCESS**
- Final stable JAR: `PlexonCore-2.1.0.jar`
- Final JAR size: `12,244,388` bytes
- Final JAR SHA-256: `6ee352f0a913aa7bce193a5f793132e893ea168f35793045191c0fa9ceea6ed6`
- Artifact ID: `10524405378`
- Artifact ZIP digest: `sha256:fa0f69bfec006065a0ff9147ebe968f48d8902539b1ad3bcf43447fe4badc77b`
- Independent downloaded-artifact checksum verification: **PASS**

## Core final build gate

Build run `35285439890` completed **SUCCESS** for the final `2.1.0` branch head.

Verified by the workflow:

- Maven `clean verify`
- complete test suite
- distribution contract
- exact embedded `plugin.yml` version
- PlexonCore main class
- BlockOrigin runtime class
- SQLite JDBC shading
- exclusion of Bukkit/Paper/Adventure server APIs from shading
- Java class major `69`
- Paper pin `26.2.build.121-stable`
- JAR SHA generation/check
- `git diff --check`

## Downstream compatibility gate

Final-version Downstream Compatibility run `35285433368` completed successfully.

Result: **14/14 PASS**

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

The compatibility run was triggered by the final POM version conversion at `71ca2bf5dafd8b16b3c5600885145bd95497ddda`. Commits after that point and before the final branch-head build altered documentation only, so no runtime/build inputs changed between the compatibility artifact and the final branch artifact.

Downstream repositories were not committed or patched.

## Release engineering state

- Project version: `2.1.0`
- Stable release notes: `.release/2.1.0.md`
- Stable workflow: `.github/workflows/release.yml`
- Stable workflow refuses prerelease version strings.
- Stable workflow requires exact `main` / `release/stable` SHA parity.
- Stable workflow rebuilds/verifies the distribution before publication.
- Release assets: `PlexonCore-2.1.0.jar` and `SHA256SUMS.txt`.
- Previous `v2.0.5` remains rollback baseline.

## Runtime verification policy

Deployment verification is retained at:

`docs/release/PLEXONCORE_2.1.0_RUNTIME_CERTIFICATION.md`

The stable artifact identity is pinned there. Live runtime evidence is recorded separately and must not be claimed unless actually collected.

## Final promotion sequence

1. Merge the verified stable branch into `main`.
2. Confirm the resulting `main` build is green.
3. Advance `release/stable` to the exact verified `main` SHA.
4. Allow the Release workflow to build and publish `v2.1.0`.
5. Verify the tag target, GitHub Release, JAR, SHA-256, and rollback baseline.
6. Record production deployment evidence after PlexonCraft is upgraded.

## Current decision

**Release policy: STABLE ONLY.**

**Deep source audit/hardening: COMPLETE.**

**Final Core Build: PASS.**

**Final downstream compatibility: 14/14 PASS.**

**Ready for stable promotion to `main` and `release/stable`.**
