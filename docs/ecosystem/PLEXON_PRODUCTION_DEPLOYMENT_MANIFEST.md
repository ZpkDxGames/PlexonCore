# Plexon Production Deployment Manifest

This is the exact source/release-certified deployment set for the Core 2.0.4 baseline. It is a deployment package definition, **not** evidence that these artifacts have already been installed on PlexonCraft.

## Installable JAR set

| Artifact | Version | SHA-256 | Classification |
|---|---:|---|---|
| `PlexonCore-2.0.4.jar` | 2.0.4 | `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonBlacksmith-1.4.1.jar` | 1.4.1 | `0c371dc03be7fec01463c2024b98e3ad804f06a7af5d45c8be848cf5733ae88e` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonChats-3.1.1.jar` | 3.1.1 | `ed544c8323e31f42b2f4fbe53655810f1082fd632adf6a89bc22b2705150865c` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonRanks-2.2.1.jar` | 2.2.1 | `c688f483977be2026e6c5bd717cd855140eae3cb01214a35f83e59891d8d4fd8` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonBackpacks-1.2.1.jar` | 1.2.1 | `1a0eb1f3a60ac3c1b1a213281876a0026124f6d54190d158c77134b4c2cec018` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonCrates-4.6.0.jar` | 4.6.0 | `9063aa118bdfe8e3d4b47d4db269ed94f858771afae9fb78f20f79dfce0af8f6` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonKeys-1.4.1.jar` | 1.4.1 | `45475f68042d9a1d025fba7aec689e390a355e5ea996bf965198e943993a28f1` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonQuests-3.3.1.jar` | 3.3.1 | `691a5d48bf24f213ab7d07cbe8925e4ef57082374829655e8d3d6d9bb2738fe1` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonShops-2.2.1.jar` | 2.2.1 | `d5824ba980e4e0ec646692f6c0d5bcf4aa8f251e67d70d4a29186742e2a93468` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonTools-4.2.1.jar` | 4.2.1 | `ba61905ae6f7a9d5c5b0043d0e51719822b22f67905325fb43b8317931849293` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonTravel-1.0.1.jar` | 1.0.1 | `39a9bdcce5a7796c3204872b7b8e42e91378b0d0508f74cc1151ab8f232bc252` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonHomes-1.0.1.jar` | 1.0.1 | `fbc68fd1f705134ae728751ce91704f63ea89413af700156f6622b41a86a5096` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonPanel-3.1.1.jar` | 3.1.1 | `ac96ad323602ca367ecfa6b2cddcd3f3a3251bb5f7f34c3195b740907c93ff40` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonSpawners-2.3.1.jar` | 2.3.1 | `626299825e188db6f89dc5eb83f74bce3ce998aa3a45ffad817ee7372d39ffb8` | CERTIFIED_WITH_RUNTIME_PENDING |
| `PlexonGPFlags-1.0.1.jar` | 1.0.1 | `fa7b60a81422d815ff777a46ce91dbfd5f877fcd39c1d5988dbc5ff67f19de82` | CERTIFIED_WITH_RUNTIME_PENDING |

Each artifact above has a corresponding GitHub Release and `SHA256SUMS.txt` in its repository release.

## Panel companion components

PlexonPanel deployment also requires the matching Host/Dashboard architecture where those capabilities are used:

- Paper agent: `PlexonPanel-3.1.1.jar` above.
- Host companion release asset: `plexonpanel-host-3.1.1.jar`, SHA-256 `e8bb99766cd39c9db157bd90f890c3d1873a65b2d1472b75d8b2c7aaabea0983`.
- Dashboard/Relay source component: `ZpkDxGames/PlexonPanel-Dashboard` 3.0.2, commit `03777c7dc108b54dda625c7f56f5e723ca35124f`, protocol 3, green CI. No JAR release is expected for the dashboard repository.

## Required exclusions

Do **not** include these in the active production JAR set:

- `PlexonClaimFlags-1.1.0.jar` / PlexonGriefPreventionAddon — deprecated and replaced by PlexonGPFlags. Running both creates overlapping enforcement.
- `PlexonSkills-1.0.0.jar` from `v1.0.0-rc.1` — prerelease/staging only unless explicitly requested for testing.
- `PlexonJobs-1.0.0.jar` from `v1.0.0-rc.1` — prerelease/SHADOW staging only.
- `PlexonUtility-1.0.0.jar` — managed source release but production activation is deferred until remaining Essentials ownership/decommission checks are performed.
- historical Plexon-DailyRewards artifacts — deprecated for this Core 2.0.4 production baseline.

## Upgrade rules

1. Stop the server and back up current plugin JARs/data.
2. Match every candidate JAR against the SHA-256 in this manifest/lockfile before installation.
3. Remove the deprecated PlexonClaimFlags JAR before enabling PlexonGPFlags; keep the old data folder until GPFlags legacy import is verified.
4. Do not deploy the draft PlexonTools 4.3.0 Runtime migration; use 4.2.1 for this baseline.
5. Preserve TheosisEconomy and Vault; no Plexon module in this manifest replaces the economy provider.
6. Start on a staging/maintenance boot and validate `/plexoncore diagnostics` plus module-specific diagnostics.
7. Only after live verification may runtime states be promoted from `NOT EXECUTED`.

## Runtime acceptance still required

The following have not been executed by this GitHub-only closure:

- production/staging install;
- actual Core `READY` observations;
- restart/hot-enable/reload cycles;
- Spark/MSPT validation;
- live custom-item/inventory/provider cases;
- travel command takeover;
- Panel end-to-end relay/pairing validation;
- load/soak testing.

`RUNTIME CERTIFICATION: NOT EXECUTED`.
