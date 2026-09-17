# PlexonCore 2.1.0 Runtime Verification

## Stable artifact identity

This checklist applies to the full stable artifact below.

- Stable version: `2.1.0`
- Final audited source line: `b259f6116ab26831ab250e0b61335908b87e71fa`
- Final stable branch-head build SHA: `f13b94210febf5ea62d03be89081d1750b49cbe9`
- Stable JAR: `PlexonCore-2.1.0.jar`
- Stable JAR size: `12,244,388` bytes
- Stable JAR SHA-256: `6ee352f0a913aa7bce193a5f793132e893ea168f35793045191c0fa9ceea6ed6`
- GitHub Actions artifact ID: `10524405378`
- Artifact ZIP digest: `sha256:fa0f69bfec006065a0ff9147ebe968f48d8902539b1ad3bcf43447fe4badc77b`
- Paper target: `26.2.build.121-stable`
- Java target: `25`
- Rollback baseline: `v2.0.5`

If the deployed JAR hash differs, identify the binary before continuing.

This document is deployment/runtime verification for the stable-only release policy. It does not imply that live-server evidence existed before publication.

## Pre-deployment

- [ ] Stop PlexonCraft cleanly.
- [ ] Record the current Core JAR filename and SHA-256.
- [ ] Confirm the accepted previous stable baseline is PlexonCore 2.0.5.
- [ ] Back up `plugins/PlexonCore/` and the current Core JAR.
- [ ] Record current Paper build and `java -version`.
- [ ] Record current plugin list and versions for active Plexon-family consumers.
- [ ] Confirm there is no duplicate PlexonCore JAR in `plugins/`.
- [ ] Copy the stable `PlexonCore-2.1.0.jar` into `plugins/`.
- [ ] Recompute SHA-256 on the server and verify the exact stable hash above.

## Startup verification

- [ ] Paper starts normally on the expected 26.2 build.
- [ ] JVM is Java 25.
- [ ] PlexonCore reports version `2.1.0`.
- [ ] PlexonCore enables without exception/error.
- [ ] No unexpected future-schema/unsupported-schema refusal.
- [ ] Core storage opens successfully.
- [ ] No repeated SQLite lock/busy/error loop.
- [ ] No scheduler rejection/backpressure storm.
- [ ] No integration discovery exception.
- [ ] No downstream Plexon plugin disables because of Core API incompatibility.
- [ ] All expected Plexon-family plugins remain enabled.

## API and integration verification

- [ ] API 2.1 reports supported.
- [ ] API 2.0 compatibility remains supported.
- [ ] API 1.0 compatibility bridge remains supported.
- [ ] Active first-party integrations report expected capability/state.
- [ ] PlexonGPFlags is identified correctly when present.
- [ ] Retired PlexonCrates is not treated as a required active dependency.
- [ ] Disabling/re-enabling a compatible consumer does not leave duplicate registrations.
- [ ] Disabling a consumer removes its Core-owned subscriptions/tasks/sessions.
- [ ] Re-enabling restores only one active registration set.

## Player-watch verification

- [ ] Join.
- [ ] Normal movement.
- [ ] Teleport within the same world.
- [ ] Teleport to another world.
- [ ] Return to the original world.
- [ ] Disconnect/reconnect.
- [ ] World-change metadata remains coherent.
- [ ] Unknown source coordinates remain explicitly unknown when unavailable.
- [ ] Destination coordinates are never mislabeled as source coordinates.
- [ ] No duplicate transition events occur from one logical transition.
- [ ] No retained player-watch registration remains after owner/plugin disable.

## Event gateway verification

- [ ] Trigger representative Core-routed events from multiple dependent plugins.
- [ ] Subscriber failure does not prevent other subscribers from receiving the event.
- [ ] Repeated subscriber failure logging is rate-limited.
- [ ] Disable/re-enable an event consumer and confirm exact-owner cleanup.
- [ ] Disabled owners fail closed and do not continue receiving events.

## GUI verification

- [ ] Open and close Core-backed GUIs normally.
- [ ] Exercise supported left/right clicks.
- [ ] Attempt unsupported inventory movement against managed slots.
- [ ] Confirm managed movement is cancelled where required.
- [ ] Change permission/state before a deferred GUI action and confirm revalidation.
- [ ] Reopen/rebuild a menu and verify stale sessions cannot mutate replacements.
- [ ] Disable the owning plugin with a GUI open and confirm safe session purge.
- [ ] Re-enable and confirm only current-generation sessions work.

## PlaceholderAPI verification

Where PlaceholderAPI is installed:

- [ ] Render representative Core-backed text/placeholders.
- [ ] Confirm normal replacement output.
- [ ] Confirm provider lifecycle refresh after controlled disable/re-enable when operationally safe.
- [ ] Confirm provider failure degrades gracefully without repeated reflection/runtime exceptions.
- [ ] Confirm no placeholder hot-path warning/error spam during normal chat/GUI rendering.

## Block-origin persistence verification

Use a controlled test area.

- [ ] Create origin-tracked block changes.
- [ ] Rapidly mutate the same tracked position multiple times.
- [ ] Confirm last-write-wins persistence after flush.
- [ ] Confirm no contradictory persisted state after rapid mutation.
- [ ] Restart and verify expected origin state survives.
- [ ] Exercise removal/cleanup and restart again.
- [ ] Observe Core origin queue/backlog during pressure.
- [ ] Confirm backlog drains.
- [ ] Perform a clean shutdown with pending origin work and verify bounded flush/close behavior.

Destructive persistence-failure injection should be performed only on staging.

## SQLite / persistence verification

- [ ] Normal reads/writes succeed.
- [ ] No persistent writer connection leak is visible.
- [ ] No repeated `database is locked` condition appears.
- [ ] Restart/reopen succeeds.
- [ ] Supported migration from the 2.0.5 data set succeeds.
- [ ] Pre-upgrade backup remains available.
- [ ] Shutdown closes storage within a bounded interval.

## Scheduler verification

- [ ] Representative async tasks complete normally.
- [ ] Health/diagnostics report successful completion.
- [ ] Controlled failing work reports failure without crashing Core.
- [ ] Subsequent successful work restores health where designed.
- [ ] Delayed tasks execute once.
- [ ] Repeating tasks can be cancelled.
- [ ] Owner/plugin disable cancels owner-scoped pending/repeating work.
- [ ] Re-enable does not duplicate repeating work.
- [ ] Server shutdown shows bounded scheduler cleanup.

## Item identity verification

Using production custom items:

- [ ] Representative custom item IDs resolve consistently.
- [ ] Exact custom items retain expected metadata/components.
- [ ] Conflicting custom-ID conditions resolve deterministically and visibly.
- [ ] Legitimate production items are not misidentified after restart.

## Diagnostics / health verification

- [ ] Overall Core health is healthy after normal startup.
- [ ] Storage contributor state is visible.
- [ ] Scheduler contributor state is visible.
- [ ] Integration state is visible.
- [ ] Origin queue/backlog is visible.
- [ ] Failure/recovery transitions are represented accurately.
- [ ] No stale degraded state remains after recovery.

## Performance observation

Run at least 30 continuous minutes after functional verification with representative Survival activity and record:

- Paper TPS
- MSPT distribution/peaks
- Spark profile or profiler snapshot
- process CPU
- heap used/committed
- GC behavior
- active task count
- thread count
- Core scheduler queue/backlog
- Core origin queue/backlog
- Core health state
- repeating warning/error signatures

Expected operational result:

- [ ] Stable 20 TPS under representative load.
- [ ] No sustained MSPT regression attributable to Core.
- [ ] No unbounded Core queue growth.
- [ ] No unbounded task/session/subscription growth.
- [ ] No obvious thread leak.
- [ ] No repeated database lock/error loop.
- [ ] No repeated integration/PlaceholderAPI failure loop.
- [ ] No unresolved HIGH or CRITICAL defect.

## Shutdown / restart verification

- [ ] Stop the server normally.
- [ ] Core shutdown completes without exception.
- [ ] Pending owned tasks are cancelled/settled.
- [ ] Block-origin persistence closes within bounded shutdown behavior.
- [ ] SQLite writers close normally.
- [ ] Start the server again.
- [ ] Core and all expected consumers re-enable once.
- [ ] No duplicate listeners/tasks/sessions are observed after restart.
- [ ] Persisted Core state remains coherent.

## Evidence record

Record:

- exact deployed JAR SHA-256
- Paper version/build
- Java version
- startup log excerpt
- Core diagnostics before/after exercise
- integration/API checks
- origin persistence/restart results
- GUI lifecycle results
- PlaceholderAPI lifecycle result
- scheduler failure/recovery result
- performance summary
- shutdown/restart excerpt
- discovered defects and severity
- deployment verification result
