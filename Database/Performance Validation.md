# Server and room performance validation

The normal avatar worker and client level requests use the trigger-maintained
projections. A source reconciliation is performed for projections never audited
or last audited at least five minutes earlier. Explicit calls to the
existing `AvatarLevelManager.refresh` methods still force a source repair.
Progress, dirty revision acknowledgements, peak levels, Pantheon convergence,
and reward operation IDs keep their existing transaction boundaries.

Migration `V20261014190000` installs component routines/triggers, initializes
disposable caches, and adds lookup indexes. Achievement,
badge, and furni triggers recalculate their own component, then apply the existing
score/peak rules. Hosting checkpoints publish the hosting change without scanning
inventory or achievements. A full refresh still recalculates all components.
Components live in independent procedures: conditional branches inside a shared
routine would still leave cross-component table dependencies in MariaDB's trigger
pre-locking graph. See [MariaDB's implicit trigger locks](https://mariadb.com/docs/server/server-usage/triggers-events/triggers/triggers-and-implicit-locks).
Migration and definition initialization seed all component caches together using
grouped updates, without modifying retained peaks or rewards during the backfill.
Definition insert/update/delete triggers adjust only affected owners' cached furni
counts when avatar eligibility changes. Ordinary price/display-name edits do not
touch avatar rows. These adjustments roll back with the definition edit, so a
later achievement cannot publish a score based on obsolete furniture eligibility.
The migration also ensures an `items(item_id)` lookup index for instance counts,
reusing equivalent existing indexes, and indexes projection counts by definition
and owner for targeted definition edits.
Atomic trades update projection rows throughout the transaction and calculate
their final score at the existing `AvatarLevelTransaction.flush` boundary; a
rollback also rolls back counters and peaks. Previous migration files are intact.

Room time/hosting timers, walking/skateboarding achievements, gym cycles, and pet
level achievements submit persistence work to the bounded JDBC executor. Each
accepted increment uses the existing achievement/reward implementation; increments
are not merged across level boundaries. Tasks resolve the current session by user
ID, including the existing durable queue path for disconnected users. Timer
submissions rejected by a full queue retain their elapsed minutes for retry.
Final exits and single action increments use the synchronous existing path if the
queue is full or stopping, preserving progress under backpressure. Accepted tasks
are drained by the runtime's existing persistence shutdown. Abrupt process failure
can still lose accepted work that has not committed, as with other deferred game
persistence; this does not promise zero latency or durable in-memory queues.
The runtime injects the queue into the achievement manager; existing public
constructors remain available, and no-argument constructors retain synchronous
legacy behavior.

Single furni editor changes reload only the affected definition and update the
existing `Item` object in place, so placed furniture retains its reference.
Deleted definitions are removed from the cache. Explicit full reloads remain
available. Rare catalog history backfills use the JDBC executor instead of the
shared game scheduler.

A room that finishes ten consecutive healthy cycles returns from the slow-worker
pool to the normal pool. A single transient database stall no longer isolates
that room permanently. Deadline order, cancellation, and non-overlapping room
execution retain the same scheduling contract.

## Regression checks

- `AvatarLevelReconcilerTest`: recently audited projections require no source
  scans or counter rewrites; expired and explicit repairs still rebuild them.
- `AvatarLevelInitializationTest`: definition reload initializes all component
  caches before committing the startup/reload transaction.
- `RoomAchievementPersistenceTest`, `RoomAchievementTimerTest`: no synchronous
  write on accepted timer submissions, exact increments, retry after saturation,
  no duplicate minutes on exit, final flush fallback.
- `ItemDefinitionRefreshTest`: single row refresh, preserved object identity,
  untouched unrelated definitions, deletion invalidation.
- `AvatarLevelMigrationIT`: fresh schema, upgrade with existing progress/peaks/
  rewards, atomic trade without transient peak inflation, rollback, and achievement/
  definition eligibility changes/rollback/restoration, and achievement/hosting
  updates while an unrelated `items_base` row lock is held.
- Existing room scheduling, movement, wired, economy, migration, and packaging
  suites must also pass. CI tests migrations on MariaDB 10.11, 11.4, and 12.3.

Actual production latency still requires comparison of slow-query logs and room
cycle timings under comparable load after deploying the new JAR and its migration.

For local Docker hosts with slow filesystem synchronization,
`POLARIS_TEST_MARIADB_TMPFS=true` places only the disposable integration database
on a 1 GiB RAM filesystem. It keeps the same container/database lifecycle and SQL
checks. The normal fixture and CI defaults are unchanged; this setting never
affects the emulator's production database.

## Local verification — 2026-10-14

All results below are local, using Temurin 25.0.4 and Maven 3.9.16. Hosted CI
has not run for these uncommitted changes. JDK 26 packaging was not checked
locally because that JDK is not installed.

| Check | Result |
| --- | --- |
| Full `-Pcoverage verify` | PASS: 2,065 unit cases, 0 failures/errors, 36 optional skips; 35 integration cases passed |
| Spotless / SpotBugs / JaCoCo gates | PASS; 0 SpotBugs findings; coverage ratchet met |
| Isolated `GameEnvironmentDisposalTest` | PASS: 5 cases |
| Migration matrix, pinned CI images 10.11.14 / 11.4.12 / 12.3 | PASS: all 15 migration/avatar cases on each version |
| Real dump, checksum, database drop and restore | PASS on MariaDB 11.4.12, using the installed MariaDB 12.3 clients |
| Opt-in `AvatarLevelLoadIT` | PASS; disk-backed results below |

The correctness suites and version matrix used the optional disposable tmpfs
fixture. **The load test used disk storage, with tmpfs disabled and
`innodb_flush_log_at_trx_commit=1`.** `sync_binlog` was 0. It used MariaDB
11.4.12 on Docker Desktop 29.8.2 on the Windows development host.

The measured phase ran 16 writers and 4 avatar refreshers against a 16-connection
pool, over 64 users. Each user owned 100 distinct badges and 32 eligible furniture
definitions; the achievement had 100 thresholds. The phase executed 10,000
achievement UPDATEs and 1,000 refresh transactions, at least 13,000 JDBC SQL
statements, excluding preparation, validation, commits and SQL inside triggers.
Refreshes invoke the actual `AvatarLevelReconciler` cache path and full component
publication routines. Latencies include connection-pool wait and transaction
completion; no failed operation is silently retried.

| Timed operation | p50 | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Achievement write | 4.664 ms | 59.665 ms | 106.430 ms | 389.626 ms |
| Avatar refresh | 88.374 ms | 113.218 ms | 135.130 ms | 233.149 ms |

Duration: **23.410 s**, combined throughput **469.89 operations/s**, **0 errors**.
All 10,000 increments were retained. Every user's progress, three raw components,
score and peak matched the expected values, and remained identical after a forced
rebuild from source tables. This is a concurrent SQL burst and integrity test;
it does not measure a full live server with connected room clients or establish
a before/after production speedup.

Reproduce from `Emulator` (omit `-o` and the local repository option when using
the normal Maven cache). The local runs used
`-Dmaven.repo.local=D:\ducket\.m2-local\repository`:

```powershell
# Full verification: Mockito's explicit agent avoids restricted Windows self-attach.
$env:POLARIS_TEST_MARIADB_TMPFS='true'
$repoCache='D:\ducket\.m2-local\repository'
$mockAgent="-javaagent:$repoCache\org\mockito\mockito-core\5.24.0\mockito-core-5.24.0.jar"
$coverageAgent="-javaagent:$repoCache\org\jacoco\org.jacoco.agent\0.8.15\org.jacoco.agent-0.8.15-runtime.jar=destfile=target/jacoco.exec,append=true"
.\mvnw.cmd -o -B "-Dmaven.repo.local=$repoCache" "-DargLine=$mockAgent $coverageAgent" -Dspotless.ratchetFrom=HEAD -Pcoverage verify

# Version matrix: set POLARIS_TEST_MARIADB_IMAGE to each pinned CI image.
.\mvnw.cmd -o -B "-Dmaven.repo.local=$repoCache" "-DargLine=$mockAgent" -DreuseForks=false -Dit.test=MigrationRunnerIT,AvatarLevelMigrationIT test-compile failsafe:integration-test failsafe:verify

# Explicit load run; normal CI skips this class unless opted in.
$env:POLARIS_TEST_MARIADB_TMPFS='false'
$env:POLARIS_TEST_MARIADB_IMAGE='mariadb:11.4.12@sha256:a794d9eb009e20de605858a11f32f63b4075cbd197c650436f0e3b457e4caed7'
.\mvnw.cmd -o -B "-Dmaven.repo.local=$repoCache" -Dpolaris.performance.load=true -Dit.test=AvatarLevelLoadIT test-compile failsafe:integration-test failsafe:verify
```

Local logs and XML results are retained in `Emulator/target/performance-matrix`,
`performance-full-verify.log`, `performance-load-run.log`, and
`performance-load.txt`. The packaged dependency JAR contains the exact validated
migration (SHA-256 `c2d9ed06f5e3d766807f48d90c17668d82b9bdba076243f1cd963f9d40c20862`).

## Follow-up: wired polling and repeated room scans

The second pass changes only `WiredInternalVariableMonitor` and
`WiredInternalVariableSupport`. There is no additional migration or protocol
change in this pass.

The variable monitor previously obtained a fresh unit/furniture snapshot for
every trigger, including duplicate subscriptions. It now identifies subscriptions
by the exact target/token pair and lazily captures each entity collection once
per poll. Nothing is cached across polls. Actual property values remain live,
and collected events are dispatched after polling, in the existing first-watch
order. Initial observation, absent values, origin, duplicate suppression and
removal of observations for inactive subscriptions retain their existing rules.
Daily task refresh still occurs before the empty-trigger fast path.

Reading or checking unit-owned properties (position, rotation, altitude, opacity,
effect, sign, dance, idle state and similar fields) no longer resolves the Habbo,
Bot and Pet owners first. Account/entity-dependent properties continue through
the existing lookup and value/presence rules. In particular, absence is still
distinct from a present zero value; the achievement-score presence rule and
missing-gender sentinel are preserved.

`WiredVariablePollingCostTest` uses 100 real RoomUnit objects, 1,000 real furniture
instances, 80 variable triggers and 1,000 consecutive accelerated polling cycles.
Room snapshots and event dispatch are controlled test doubles: this is a monitor
regression/stress check, not 100 connected game clients or a room scheduler load
benchmark. It checks every expected change, duplicates, unchanged values,
subscription removal/reinstallation, null inputs, account-dependent values and
the number of snapshot/identity lookups.

| Work per full poll in this fixture | Previous implementation | Optimized implementation |
| --- | ---: | ---: |
| Unit collection snapshots | 40 | 1 |
| Furniture collection snapshots | 40 | 1 |
| Habbo/Bot/Pet lookup calls for position/altitude | 1,200 | 0 |

The snapshot reduction is **97.5% in this fixture**; it is not a claim of a
97.5% whole-server speedup. The earlier disk SQL benchmark is separate and is
not reused as a measurement of these wired changes.

Local focused commands (same repository/agent options as above):

```powershell
.\mvnw.cmd -o -B "-Dmaven.repo.local=$repoCache" "-DargLine=$mockAgent" '-Dtest=WiredVariablePollingCostTest,*InternalVariable*Test,WiredNativeMovementParityTest' test
.\mvnw.cmd -o -B "-Dmaven.repo.local=$repoCache" "-DargLine=$mockAgent" -Dtest=WiredVariablePollingCostTest test
```

Both passed: 24 focused cases, followed by the expanded 1,000-cycle stress
case and its two property-compatibility cases (3/3 passed). The pre-change
regression run confirmed the redundant lookups and snapshots. Logs:
`target/performance-round2-before.log`, `performance-round2-focused.log`, and
`performance-round2-cycles.log`.

The final full `-Pcoverage verify` run completed successfully at 23:04 Europe/Rome
on 2026-10-14 (16 min 16 s):

- 2,068 unit cases: 0 failures/errors, 36 optional skips.
- 36 integration cases: 35 passed, 0 failures/errors, 1 skipped opt-in SQL load
  test (its separate disk-backed run is recorded above).
- Spotless: all 29 changed Java files clean.
- SpotBugs: 0 findings and 0 analysis errors.
- JaCoCo: all coverage checks met, using a fresh execution-data file.
- Packaged class bytes, including the new subscription record, match the tested
  class files; the packaged migration also matches the validated SQL.

Full log: `Emulator/target/performance-round2-full-verify.log`. These are local
results; no hosted CI was run. The SQL migration is unchanged from the earlier
three-version matrix. JDK 26 remains untested locally.

The artifact validated in that wired pass was
`Emulator/target/Polaris-4.2.71-jar-with-dependencies.jar`, with SHA-256
`ef7db08a3af0ca967e90b9d2c69f6ca792b48f86c628ecc64e386f2baf5afad0`;
That checksum identifies the earlier wired-only build; the current cumulative
artifact and its adjacent `.sha256` file are recorded in the follow-up below.
Nothing has been deployed to the production server.

## Follow-up: daily tasks and reward-track progress

The new 23:26–23:28 log excerpt contains 78 DEBUG lines, with no WARN, ERROR or
slow-query entry. There are 38 daily-task ignored messages, one daily-task
progress message, 38 reward-track `rows=0` messages and one `rows=2` message.
An ignored action advances no eligible unfinished daily task. An upsert's
affected-row count of two can describe one existing progress row incremented
once; it does not demonstrate duplicate prizes.

Inspection revealed a separate resource-lifetime problem in the progress paths:
the reward writer held its connection while invoking daily progress, and the
daily writer/readers held theirs while invoking wired/achievement callbacks
that can borrow another connection. A real MariaDB test with a single-connection
pool reproduced a 251 ms borrow timeout and a missing daily increment before
the fix. This is a code/test finding, not evidence that the supplied DEBUG
excerpt itself contains a timeout.

Reward progress, wired progress/reset, daily progress and their achievement
publication now release their own connection before calling the next component.
The daily write still commits/rolls back at the same boundaries, before its
notifications. Generic reward actions still reach daily tasks even when the
reward upsert reports zero affected rows. Notification order, progress caps,
completion checks, prize claims, premium purchases, action hooks and DEBUG
messages retain their previous behavior. No negative-action cache or additional
database migration was introduced.

The daily streak calculation now reads ordered distinct completion dates in one
query, stopping at the first gap. It retains the original requirement to have a
completion today, counts each day once and excludes future dates from streaks.
The all-time completed-task count remains a separate query and still includes
all completed rows. Its achievement notification still occurs before reading
the streak, and each callback runs after its read connection has been released.
Thus achievement synchronization takes two history reads regardless of streak
length, instead of one extra SQL statement per day.

`TaskProgressPersistenceIT` uses the real migrated schema and real managers,
with controlled wired/achievement callbacks. It checks a one-connection pool,
365 historical days, duplicate completions, future dates, gaps, an unfinished
current day, single upsert increments, daily-only actions, wired add/replace/reset
and preservation of previously claimed rewards. Callback checks borrow the same
single pool slot to prove no parent connection remains held.

The test also executes 1,000 unmatched gameplay actions like the repeated log
entries. Each tested MariaDB version completed **7,000 prepared SQL statements**
for that phase with the expected unchanged state and claims. These are sequential
manager/SQL tests with callbacks under test control, not a connected-user server
throughput benchmark. The integration databases used the disposable tmpfs fixture.

Local focused checks passed: 6 unit cases and the MariaDB integration case, followed
by the expanded 1,000-action case on all three pinned CI images (10.11.14,
11.4.12 and 12.3). Logs and XML reports are retained under
`Emulator/target/performance-tasks-matrix`, with the initial reproduction in
`performance-tasks-before.log` and focused checks in `performance-tasks-focused.log`.

Focused commands used the repository and explicit Mockito agent options above:

```powershell
# After compiling the updated sources, repeat with each pinned CI image.
.\mvnw.cmd -o -B "-Dmaven.repo.local=$repoCache" "-DargLine=$mockAgent" -Dit.test=TaskProgressPersistenceIT failsafe:integration-test failsafe:verify
```

No production database, runtime configuration or client code was modified.

The final cumulative local `-Pcoverage verify` run completed successfully at
23:57:56 Europe/Rome on 2026-10-14 (7 min 43 s):

- 2,068 unit cases: 0 failures/errors, 36 optional skips.
- 37 integration cases: 36 passed, 0 failures/errors, 1 skipped opt-in SQL load
  test (its separate disk-backed run is recorded above).
- Spotless: all 32 changed Java files clean.
- SpotBugs: 0 findings and 0 analysis errors.
- JaCoCo: all coverage checks met, using a fresh execution-data file.
- The new integration case again completed its 1,000-action / 7,000-statement
  phase with pool size one and the expected progress/claim state.

Full log: `Emulator/target/performance-tasks-full-verify.log`. The command used
the pinned MariaDB 11.4 image, the explicit Mockito and JaCoCo agents, and
`-Dspotless.ratchetFrom=HEAD -Dspotbugs.maxHeap=1024 -Pcoverage verify` with the
offline repository options above. The heap flag only gives the local static
analyzer more memory; it does not change the production JVM or disable checks.
These are local JDK 25 results; no hosted CI or JDK 26 run was performed.

The final cumulative artifact is
`Emulator/target/Polaris-4.2.71-jar-with-dependencies.jar`, with SHA-256
`b4e1339cc602aeea11ba07a8474caec5e5a45c1fa13f61a6e87c9dd55ceadaad`;
the adjacent `.sha256` file records the same value. Packaged daily-task,
reward-track and wired-monitor class bytes match the tested class files,
including the subscription record. The packaged migration still matches both
the source and the previously validated checksum
`c2d9ed06f5e3d766807f48d90c17668d82b9bdba076243f1cd963f9d40c20862`.
Nothing has been committed, pushed or deployed.
