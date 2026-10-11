# Maven build review — 2026-10-09

Initial command: `mvnw.cmd -B clean package` (no skipped-test flag).
Initial result: compilation passed; 1,727 tests, 7 failures, 13 errors, 10 conditional skips.

## Runtime and harness repairs

- Daily-task WIRED constant 115 shadowed inherited public CODE=70. Renamed only the implementation constant to DAILY_TASK_CODE; wire value remains 115, inherited plugin constant remains 70. Released-jar ABI test passes without changing accepted divergences.
- Furniture writes attempted to read unsupported wall-furniture gravity before the existing rejection. Previous-value telemetry now checks capability first; supported writes remain unchanged.
- Runtime-state tests mocked RoomWiredRuntime without its variable monitor. Stubs now provide the monitor; the real room lifetime/monitor is unchanged.
- Registry discovery now resolves fully qualified source class names as well as imports. Full matrices explicitly cover 257 currently registered types.
- Six empty exception handlers retain their previous fallback and emit bounded, payload-free diagnostics using the existing diagnostic service.

## Reviewed fixture updates

These are snapshots of functionality already present before this build repair, not runtime changes introduced to make tests pass.

| Fixture | Added lines | Removed lines |
| --- | ---: | ---: |
| public-surface-v1.txt | 324 | 42 |
| interaction-protocol-v1.txt | 218 | 213 |
| outgoing-packets-v1.txt | 1 | 1 |
| registered-execution-v1.txt | 7 | 3 |
| persistence-matrix-v1.txt | 168 | 122 |

- Interaction registry: native aliases/instance conditions, daily-task variables and chest scanning already registered in ItemManager. Wire layout/header constants are unchanged by this repair.
- Registered execution: existing signal receiver probe changes and new condition/daily-task/chest types. Persistence changes include existing native timer normalization, native sources/snapshots, variables, quests and transactions. Their current serialization and fallback semantics are preserved rather than reverted to the older fixture.
- Public surface: current native settings/helpers and new features are captured. Several old declared methods are inherited from new implementation parents; filter/quest validation adds checked save exceptions, timer fields are volatile, and new event types change the reflected enumeration snapshot. This repair does not change those pre-existing behaviors. Released-plugin binary ABI is independently verified.
- Packet golden bytes: only variables-empty changes. Its previous header/payload prefix remains intact; the capability extension marker -2026100402 and zero capability count append eight bytes. Frame length reflects the tail.
- Packet manifest: ROOM_CREATE has the pre-existing optional bytesAvailable-gated vocalChatEnabled boolean. Five pre-existing custom quest/chest headers (9342, 9343, 9370, 9372, 9373) are explicitly classified as Java custom extensions; legacy packet schemas are unchanged.
- Architecture snapshot: reviewed current global lookups for existing collectibles, quests, native WIRED signals/transactions/settings and log persistence; the strict future-growth check remains enabled. No service lookup or runtime lifetime was altered.

## Initial error groups

FrozenArchitectureBaselineTest; WiredModernizationQualityGateTest; WiredPersistenceCompatibilityTest; WiredRegisteredExecutionCompatibilityTest; WiredInteractionProtocolContractTest; WiredGravityInternalVariableTest; WiredFurniRuntimeStatePolicyTest; WiredFurniRuntimeStateRequestEventTest; PacketContractCatalogTest; PacketContractCoverageTest; WiredPacketGoldenCompatibilityTest; PluginAbiCompatibilityTest; WiredPublicSurfaceCompatibilityTest.

The next full run identified the persistence fixture taxonomy counter as stale after matrix review. Exact counts are now blank=18, json-empty=20, malformed=98, legacy-zero=13, legacy-tab=18; exception counts EOF=82, NPE=72, number-format=12, array-index=1. These characterize deliberately incomplete synthetic loader inputs, not test errors or accepted production exceptions. The exact-count assertions remain enabled.

## Final validation

`mvnw.cmd -B clean package` succeeded without any skip-test or regeneration properties.
Tests: 1727; failures: 0; errors: 0; conditional skips: 10.
Runtime package: `target/Polaris-4.2.67-jar-with-dependencies.jar`. No server was restarted or deployed.
Full build log: `target/build-review/full-package-final.log`.
