# NordGuard 0.5.0-rc.1

Release candidate for Minecraft 26.2, Paper and Folia, Java 25. Not a stable complete anticheat.

## Scope

The existing 17 movement, combat and block checks retain their behavior and default to OBSERVE. There are no automatic kicks or bans. Existing configurations and data are not overwritten. Production was not accessed or modified.

The new ordinary movement predictor is a standalone prototype, not a runtime check. It does not yet provide packet-based movement correction or a complete latency-compensated client simulation. The remaining implementation and validation gates are listed in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/v0.5.0-rc.1/TESTING.md).

## Changes

- Detached outbound chunk/block snapshots and bounded owner-thread decoding for the 26.2 format.
- Opt-in block cache: `packets.world-replica: false` by default. Missing or uncertain blocks remain unknown. Global copying, decoding and materialization budgets limit work.
- Ordinary static block-shape extraction, AABB clipping and step-up kernel.
- Standalone predictor with at most two calculated states and 36 candidate trials; unchecked observed velocity never becomes the model's velocity.
- Packet observer installation before the first scheduled sample, with cleanup if the entity scheduler rejects the task.
- Cache diagnostics in `/nordguard inspect <player>`; cache toggles take effect on reload without disabling the original checks.

## Validation

155 unit tests passed locally and in GitHub Actions. The identical JAR passed 80 runtime assertions on Paper and 80 on Folia; both isolated servers stopped cleanly. Runtime results, observed timing outliers and unvalidated cases are recorded in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/v0.5.0-rc.1/TESTING.md). Synthetic model workloads are not evidence of capacity at 600 online players.

The JAR contains no test probes, worlds, logs, player data or bundled Netty dependency.

SHA-256: `c95c45ca3fe577c3fe7310d53aa77d5b5fe7bbcef9bd10fb6dedbad61a0ee744`
