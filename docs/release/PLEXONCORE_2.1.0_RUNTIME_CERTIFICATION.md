# PlexonCore 2.1.0 Runtime Certification

## Candidate identity

This checklist applies only to the exact runtime candidate below.

- Candidate version: `2.1.0-rc.1`
- Certified source SHA: `b259f6116ab26831ab250e0b61335908b87e71fa`
- Candidate JAR: `PlexonCore-2.1.0-rc.1.jar`
- Candidate JAR SHA-256: `ae32e9722eca8fea2ccbe161e2748181349b6c2ec59f09962133094620293375`
- Paper target: `26.2.build.121-stable`
- Java target: `25`
- Rollback baseline: `v2.0.5`

If the JAR hash differs, stop certification and identify the binary before continuing.

## Pre-deployment

- [ ] Stop PlexonCraft cleanly.
- [ ] Record the current Core JAR filename and SHA-256.
- [ ] Confirm the currently accepted stable baseline is PlexonCore 2.0.5.
- [ ] Back up `plugins/PlexonCore/` and the current Core JAR.
- [ ] Record current Paper build and `java -version`.
- [ ] Record current plugin list and versions for all active Plexon-family consumers.
- [ ] Confirm there is no duplicate/old PlexonCore JAR in `plugins/`.
- [ ] Copy the exact RC JAR into `plugins/`.
- [ ] Recompute SHA-256 on the server and verify it exactly matches the candidate hash above.

## Startup gate

Start PlexonCraft normally and capture the complete startup section.

Pass criteria:

- [ ] Paper starts normally on the expected 26.2 build.
- [ ] JVM is Java 25.
- [ ] PlexonCore reports version `2.1.0-rc.1`.
- [ ] PlexonCore enables without exception/error.
- [ ] No future-schema or unsupported-schema refusal is triggered unexpectedly.
- [ ] Core storage opens successfully.
- [ ] No repeated SQLite lock/busy/error loop occurs.
- [ ] No scheduler rejection/backpressure storm appears.
- [ ] No integration discovery exception appears.
- [ ] No downstream Plexon plugin disables because of Core API incompatibility.
- [ ] `/plugins` / equivalent confirms all expected Plexon-family plugins are enabled.

Immediately fail the runtime gate for any unresolved startup `Exception`, `Error`, linkage failure, schema refusal, repeated storage failure, or dependent-plugin disablement attributable to Core.

## API and integration gate

Verify active consumers against the live Core service.

- [ ] API 2.1 reports supported.
- [ ] API 2.0 compatibility remains supported.
- [ ] API 1.0 compatibility bridge remains supported.
- [ ] Active first-party integrations are detected with expected capability/state.
- [ ] PlexonGPFlags is identified correctly when present.
- [ ] Retired PlexonCrates is not treated as a required/active first-party dependency.
- [ ] Disabling and re-enabling a compatible consumer does not leave duplicate registrations.
- [ ] Disabling a consumer removes its Core-owned subscriptions/tasks/sessions.
- [ ] Re-enabling restores only one active registration set.

## Player-watch transition gate

Exercise at least two players when possible.

- [ ] Join.
- [ ] Normal movement.
- [ ] Teleport within the same world.
- [ ] Teleport to another world.
- [ ] Return to the original world.
- [ ] Disconnect/reconnect.
- [ ] World-change metadata remains coherent.
- [ ] Unknown source coordinates are reported as unknown when the platform cannot supply them; destination coordinates are never mislabeled as source coordinates.
- [ ] No duplicate transition events occur from one logical transition.
- [ ] No retained player-watch registration remains after owner/plugin disable.

## Event-gateway gate

- [ ] Trigger representative Core-routed events from multiple dependent plugins.
- [ ] Confirm one failing synthetic/test subscriber, if safely reproducible, does not prevent other subscribers from receiving the event.
- [ ] Confirm repeated subscriber failure logging is rate-limited rather than spammed.
- [ ] Disable/re-enable an event consumer and confirm exact-owner cleanup.
- [ ] Confirm disabled owners fail closed and do not keep receiving events.

## GUI gate

Use every Core-backed GUI path available from active production consumers.

- [ ] Open and close normally.
- [ ] Click managed buttons using normal left/right clicks as applicable.
- [ ] Attempt shift-click/double-click/number-key/offhand or other unsupported movement into managed inventory regions.
- [ ] Verify managed movement is cancelled where required.
- [ ] Change permission/state while a deferred GUI action is pending and confirm action revalidation.
- [ ] Reopen/rebuild a menu and confirm a stale holder/session cannot mutate the replacement session.
- [ ] Disable the owning plugin with a GUI open; confirm sessions are purged safely.
- [ ] Re-enable and confirm only current-generation sessions work.

## PlaceholderAPI lifecycle gate

Where PlaceholderAPI is installed:

- [ ] Render representative Core-backed text/placeholders.
- [ ] Confirm normal replacement output.
- [ ] Disable/re-enable PlaceholderAPI during a controlled maintenance test if operationally acceptable.
- [ ] Confirm Core invalidates/refreshes the provider adapter correctly.
- [ ] Confirm provider failure degrades gracefully rather than causing repeated reflection/runtime exceptions.
- [ ] Confirm no placeholder-related hot-path warning/error spam during normal chat/GUI rendering.

If PlaceholderAPI cannot be safely toggled on production, perform this gate on an equivalent staging clone using the exact candidate JAR and record that limitation.

## Block-origin persistence gate

Use a controlled test area.

- [ ] Create origin-tracked block changes.
- [ ] Rapidly mutate the same tracked position multiple times.
- [ ] Confirm last-write-wins persistence after flush.
- [ ] Confirm no duplicate/contradictory persisted state after rapid mutation.
- [ ] Restart and verify expected origin state survives.
- [ ] Exercise removal/cleanup and restart again.
- [ ] Observe Core origin queue/backlog during pressure.
- [ ] Confirm backlog drains.
- [ ] If a safe storage-failure simulation is available on staging, verify failure becomes visible and retry/recovery succeeds.
- [ ] Perform a clean shutdown with pending origin work and verify bounded flush/close behavior.

Do not intentionally corrupt or make the production database unwritable solely to satisfy the failure-injection item; use staging for destructive failure tests.

## SQLite / persistence gate

- [ ] Normal reads/writes succeed.
- [ ] No persistent writer connection leak is visible.
- [ ] No repeated `database is locked` condition appears.
- [ ] Restart/reopen succeeds.
- [ ] Supported migration path from the 2.0.5 data set succeeds.
- [ ] Backup remains available before migration.
- [ ] Transaction failure does not leave a visibly partial logical operation in any safely exercised path.
- [ ] Shutdown closes storage within a bounded interval.

## Scheduler gate

- [ ] Representative async tasks complete normally.
- [ ] Health/diagnostics report successful completion after joined/completed tasks.
- [ ] Controlled failing task reports failure without crashing Core.
- [ ] A subsequent successful task restores the scheduler contributor to healthy state where designed.
- [ ] Delayed task executes once.
- [ ] Repeating task can be cancelled.
- [ ] Owner/plugin disable cancels owner-scoped pending/repeating work.
- [ ] Re-enable does not duplicate repeating work.
- [ ] Server shutdown shows bounded scheduler cleanup with no lingering shutdown wait.

## Item identity gate

Using production custom items that rely on Core identity/fingerprints:

- [ ] Representative custom item IDs resolve consistently.
- [ ] Exact custom items retain metadata/NBT/components expected by dependent plugins.
- [ ] Conflicting/duplicate custom-ID conditions, if present in configuration, resolve deterministically and visibly.
- [ ] No legitimate production item is misidentified after restart.

## Diagnostics / health gate

Capture Core diagnostics under idle and exercised conditions.

- [ ] Overall Core health is READY/healthy after normal startup.
- [ ] Storage contributor state is visible.
- [ ] Scheduler contributor state is visible.
- [ ] Integration state is visible.
- [ ] Origin queue/backlog is visible.
- [ ] Failure/recovery transitions are represented accurately for any safely exercised failure case.
- [ ] No stale DEGRADED state remains after a successfully recovered condition.

## Performance / soak gate

Run at least **30 continuous minutes** after the functional gates with representative Survival activity.

Record:

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
- any repeating warning/error signature

Pass criteria:

- [ ] Stable 20 TPS under the observed representative load.
- [ ] No sustained MSPT regression attributable to Core.
- [ ] No unbounded Core queue growth.
- [ ] No unbounded task/session/subscription growth.
- [ ] No obvious thread leak.
- [ ] No repeated database lock/error loop.
- [ ] No repeated integration/PlaceholderAPI reflection failure loop.
- [ ] No unresolved HIGH or CRITICAL defect.
- [ ] No new severe startup/runtime error signature.

## Shutdown / restart gate

After soak:

- [ ] Stop the server normally.
- [ ] Core shutdown completes without exception.
- [ ] Pending owned tasks are cancelled/settled.
- [ ] Block-origin persistence closes within its bounded shutdown behavior.
- [ ] SQLite writers close normally.
- [ ] Start the server again.
- [ ] Core and all expected consumers re-enable once.
- [ ] No duplicate listeners/tasks/sessions are observed after restart.
- [ ] Persisted Core state remains coherent.

## Evidence to record

Paste or attach the evidence needed to close the execution ledger:

- Exact deployed JAR SHA-256
- Paper version/build
- Java version
- startup log excerpt
- Core diagnostics before exercise
- Core diagnostics after exercise
- relevant integration/API checks
- origin persistence/restart results
- GUI lifecycle results
- PlaceholderAPI lifecycle result or staging limitation
- scheduler failure/recovery result
- 30-minute Spark/performance summary
- shutdown/restart excerpt
- discovered defects with severity
- final runtime PASS/FAIL decision

## Certification decision

- [ ] **PASS** — exact candidate can proceed to stable-version repack/rebuild and final CI gates.
- [ ] **FAIL** — candidate must not be promoted. Record defect(s), cut a new RC, and repeat source/CI/downstream/runtime gates as applicable.

Stable publication must remain blocked until this checklist is completed against the exact candidate hash above.
