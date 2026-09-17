# PlexonCore 2.1.0 Release Execution

## Release state

`IMPLEMENTING`

## Verified baseline

- Repository: `ZpkDxGames/PlexonCore`
- Baseline stable tag: `v2.0.5`
- Baseline `main` SHA: `f043828525c0c0e9afd4d3a5f8863082a8d08778`
- Baseline `release/stable` SHA: `f043828525c0c0e9afd4d3a5f8863082a8d08778`
- Baseline stable JAR: `PlexonCore-2.0.5.jar`
- Baseline stable JAR SHA-256: `bf4df796e83571e76c06b296c75c5e73ddc8053cb2c587d74c08cd3922e0b4d4`
- Target release: `2.1.0`
- Paper target: `26.2.build.121-stable`
- Java target: `25` / class major `69`
- Baseline API: `2.0`, retaining the `1.0` compatibility bridge
- Open pull requests at reconnaissance: none
- `main` protection at reconnaissance: disabled

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

## Implementation phases

1. Event gateway failure isolation and owner lifecycle
2. Player-watch transition correctness and owner lifecycle
3. Block-origin coalesced persistence and writer lifecycle
4. Scheduler observability and owner lifecycle
5. Contributor-based Core health aggregation
6. Capability-aware integration registry
7. GUI security, stale-session rejection and owner lifecycle
8. Cached PlaceholderAPI adapter lifecycle
9. Item identity/fingerprint hardening
10. Bounded near-O(1) event dedupe
11. Configuration validation/restart-required semantics
12. Additive Core API 2.1 contract
13. Downstream compatibility checks
14. Expanded regression tests
15. Performance validation
16. Exact-candidate runtime certification
17. Release engineering and governance closure

## Runtime gates

Stable publication is blocked until the exact RC artifact is tested on PlexonCraft with:

- Paper 26.2
- Java 25
- exact candidate SHA-256
- upgrade from 2.0.5 data
- API bridge and integration checks
- owner lifecycle disable/re-enable checks
- player movement/world-change/teleport/disconnect checks
- block-origin rapid mutation/restart/failure-retry checks
- scheduler failure/cleanup checks
- GUI permission checks
- PlaceholderAPI lifecycle checks
- at least 30 minutes of Spark/task/thread/heap/origin-queue observation
- no unresolved HIGH/CRITICAL defects

## Release evidence to fill at closure

- Candidate source SHA: pending
- Candidate JAR: pending
- Candidate SHA-256: pending
- Final test count: pending
- CI status: pending
- Downstream compatibility: pending
- Runtime certification: pending
- Spark/performance result: pending
- Stable source SHA/tag: pending
- Rollback verification: baseline `v2.0.5`; data compatibility pending final persistence decision
