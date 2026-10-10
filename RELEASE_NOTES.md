# NordGuard 0.5.0-rc.3

Release candidate for Minecraft 26.2, Paper and Folia, Java 25. Not a stable complete anticheat.

## Scope

The existing 17 movement, combat and block checks retain their behavior and default to OBSERVE. There are no automatic kicks or bans. Existing configurations and data are not overwritten. Production was not accessed or modified.

Stage 2 connects ordinary packet prediction to acknowledged static block geometry in observation only. There is no new packet-based movement correction or complete latency-compensated client simulation. The remaining gates are listed in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/v0.5.0-rc.3/TESTING.md).

## Changes

- Move/TickEnd frame assembly drives the ordinary predictor on the entity owner; omitted movement does not stop standing ticks.
- Scene collection reads acknowledged IDs only and covers both branches, step-up and extended-shape halo. Missing geometry, support/friction ambiguity and unsupported contexts defer.
- Five-frame supported-rest acquisition, persistent calculated momentum, transition resets and reacquisition after repeated mismatches.
- Two frames per owner drain, 512 cells / 256 shapes per scene, 36 candidate trials maximum, plus configurable global frame/cell budgets and monotonic tick credit.
- `prediction.enabled: false` by default; enabling it requires packet collection and the world cache. Invalid reloads retain the old settings.
- `/nordguard inspect <player>` reports accepted frames, mismatches, deferred frames and trial counts. Mismatches do not alert, punish or increment the existing check counters.

Pong does not prove client obedience. Unknown data do not become air or trigger punishment. Server attributes are owner snapshots, not complete acknowledged historical state. Entity collisions, special movement and client/server transitions remain validation gaps; diagnostic mismatches are not proof of cheating. There is no forced chunk loading or resending.

## Validation

202 unit tests passed locally. Runtime results and remaining validation limits are recorded in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/v0.5.0-rc.3/TESTING.md). Synthetic model workloads are not evidence of capacity at 600 online players.

The JAR contains no test probes, worlds, logs, player data or bundled Netty dependency.

SHA-256: `c9abae01427da67bf58311489a9a116e8faabd41a2f281bae5d2bc4a4d563109`
