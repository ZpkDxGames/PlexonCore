# Plexon Ecosystem Baseline

Certification snapshot: 2026-09-09.

## Core baseline

- PlexonCore: `2.0.4`
- Core release commit: `83571edb428472649af87be9d52868fc8f325806`
- Core API: `2.0`
- Paper target: `26.2.build.121-stable`
- Java target: `25`
- JAR: `PlexonCore-2.0.4.jar`
- SHA-256: `61d625a717da9f46ee9231e1970d84b4c317ae12cf4090cdf7c9d39b6a1a9baf`

Core 2.0.4 is the frozen certification baseline. This closure does not require a Core version bump.

## Certified active source/release set

| Module | Version | Release commit | JAR SHA-256 |
|---|---:|---|---|
| PlexonBlacksmith | 1.4.1 | `2fca852d0c2e9efddd9d8d55865c7b5c0c46fcb5` | `0c371dc03be7fec01463c2024b98e3ad804f06a7af5d45c8be848cf5733ae88e` |
| PlexonChats | 3.1.1 | `c7191c21654b11ea55c03e5fecae629e20898319` | `ed544c8323e31f42b2f4fbe53655810f1082fd632adf6a89bc22b2705150865c` |
| PlexonRanks | 2.2.1 | `8f6fd363959041faf13399cd24bd315fa55176e8` | `c688f483977be2026e6c5bd717cd855140eae3cb01214a35f83e59891d8d4fd8` |
| PlexonBackpacks | 1.2.1 | `488faf508d0708e60b2caab64a97a7e63b06a7c8` | `1a0eb1f3a60ac3c1b1a213281876a0026124f6d54190d158c77134b4c2cec018` |
| PlexonCrates | 4.6.0 | `261270243beb9ff4a8992f53095f16c20458f770` | `9063aa118bdfe8e3d4b47d4db269ed94f858771afae9fb78f20f79dfce0af8f6` |
| PlexonKeys | 1.4.1 | `c06a9e4107fc259f45d32d1f0767e5e2b5d1d872` | `45475f68042d9a1d025fba7aec689e390a355e5ea996bf965198e943993a28f1` |
| PlexonQuests | 3.3.1 | `b74fc212d5aea41a0e01e9bc1bf5c5382302d014` | `691a5d48bf24f213ab7d07cbe8925e4ef57082374829655e8d3d6d9bb2738fe1` |
| PlexonShops | 2.2.1 | `81c77e936194de2d46fd40221ea57a1f65c07b34` | `d5824ba980e4e0ec646692f6c0d5bcf4aa8f251e67d70d4a29186742e2a93468` |
| PlexonTools | 4.2.1 | `6e7a285fba8c16fc647ccc22c2c8342b2eb96711` | `ba61905ae6f7a9d5c5b0043d0e51719822b22f67905325fb43b8317931849293` |
| PlexonTravel | 1.0.1 | `772188d55c694deea0bc5ae0bdce0898513499e5` | `39a9bdcce5a7796c3204872b7b8e42e91378b0d0508f74cc1151ab8f232bc252` |
| PlexonHomes | 1.0.1 | `d20bef0a76cd3006df51d46d67a3e48636650d31` | `fbc68fd1f705134ae728751ce91704f63ea89413af700156f6622b41a86a5096` |
| PlexonPanel | 3.1.1 | `e0984b625d692de6076afa7e20c4fe4b35f07e9a` | `ac96ad323602ca367ecfa6b2cddcd3f3a3251bb5f7f34c3195b740907c93ff40` |
| PlexonSpawners | 2.3.1 | `0ec54a04ecb77374874edf889b20286144c32a88` | `626299825e188db6f89dc5eb83f74bce3ce998aa3a45ffad817ee7372d39ffb8` |
| PlexonGPFlags | 1.0.1 | `757f62fa52fdb6ffa718c57ab4515b60c5aa23f3` | `fa7b60a81422d815ff777a46ce91dbfd5f877fcd39c1d5988dbc5ff67f19de82` |

Every row above has source provenance, green release-line CI, a GitHub Release, the named JAR, and `SHA256SUMS.txt`.

## Component classifications outside the deployment set

- `PlexonPanel-Dashboard` 3.0.2: managed source/deployment component at `03777c7dc108b54dda625c7f56f5e723ca35124f`; protocol 3; green dashboard/relay CI; no JAR release is expected.
- `PlexonSkills` 1.0.0-rc.1: `PRERELEASE_NOT_CERTIFIED`; target `bc7eb72271e4979a7d4095b90482739593c54f7e`; major premium rework is the next phase.
- `PlexonJobs` 1.0.0-rc.1: `PRERELEASE_NOT_CERTIFIED`; target `e209dbc6e744882ae6cd5fd2426302f07f647010`; stable promotion requires real migration/SHADOW/PRIMARY/load/soak evidence.
- `PlexonUtility` 1.0.0: `PLANNED_NOT_ACTIVE` for the production manifest. A source release exists, but Essentials command-ownership/decommission staging is explicitly unresolved.
- `Plexon-DailyRewards`: `DEPRECATED` for this Core 2.0.4 ecosystem baseline; historical standalone release lineage only.
- `PlexonGriefPreventionAddon` / `PlexonClaimFlags`: `DEPRECATED`; replaced by PlexonGPFlags. Historical 1.1.0 remains rollback-only and must not be deployed together with GPFlags.

## Authority decisions

- PlexonTools 4.2.1 is `LOCAL_AUTHORITY / CORE_REGISTERED`. The draft 4.3.0 Runtime migration is not promoted.
- PlexonGPFlags is the single active GriefPrevention flags/control plugin. GriefPrevention remains authoritative for claims, trust, ownership, subdivisions and persistent claim IDs.
- TheosisEconomy remains the economy provider. Plexon modules integrate through Vault and do not replace the provider.
- Panel Paper/Host 3.1.1 remains protocol-3 compatible with Dashboard/Relay 3.0.2; the certification patch did not alter pairing, identity, grants, relay authentication or `/v1` protocol behavior.

## Runtime boundary

`RUNTIME CERTIFICATION: NOT EXECUTED`.

No production deployment, observed Core `READY` state, Spark PASS, restart PASS, soak PASS, live GUI validation or live command-takeover claim is made by this GitHub/source/release certification.
