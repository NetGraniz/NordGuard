# NordGuard 0.5.0-rc.4

Release candidate for Minecraft 26.2, Paper and Folia, Java 25. Not a stable complete anticheat.

## Scope

Stage 3 hardens transitions and the existing movement setbacks. Ordinary packet prediction remains opt-in, observation-only and disabled by default. This build does not add complete fluid, elytra or vehicle physics, nor predictor-based punishment. All 17 existing checks still default to OBSERVE. There are no automatic bans or kicks.

Production was not accessed or modified. Existing configurations remain compatible and are not overwritten. No new database, dependency, repeating timer, packet logging or chunk-resend mechanism was added.

## Changes

- Prediction waits for the latest observed outbound teleport's matching confirmation. Stale confirmations and velocity/owner resets cannot release that wait. Enabling prediction while the timeline already awaits confirmation inherits the pending ID.
- Disabled gravity, server-reported climbing and immersion explicitly defer ordinary prediction. Existing WaterWalk, Climb, NoWeb and NoSlow envelopes remain separate; unsupported states are not claimed as simulated.
- Setback operation tickets reject late completions after an external transition, timeout or newer return. A five-second monotonic limit expires bookkeeping at the next available owner tick or completion; it cannot cancel the server teleport or wake a suspended Folia entity.
- Anchors must be less than 30 seconds old, within 64 blocks, in the same world and inside the border. Destination support, clearance, loaded chunks and region ownership are checked before dispatch and again on completion.
- Completion also checks player eligibility, world, distance and native teleport sequence. Movement received before the owner callback does not require exact coordinate equality. Failed or uncertain returns discard the anchor instead of promoting it.
- Ground-supported clean positions beside ladders or inside webs remain usable; ladders and webs alone are not solid support. This preserves the existing medium checks without treating liquid surfaces as supported destinations.

The extra destination probe runs only for a completed candidate return, not for every movement packet. Monotonic age/ticket checks use the existing entity task and constant-sized state. This design does not establish capacity at 600 players or a performance comparison with another anticheat. Pong and teleport confirmations do not prove client obedience. Unknown data do not become air or trigger punishment.

## Validation

217 unit tests passed locally and in GitHub Actions. The identical final JAR passed all 90 full runtime assertions on Paper and 90 on Folia; both isolated servers stopped cleanly. Runtime results and failed development attempts are recorded in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/v0.5.0-rc.4/TESTING.md). Synthetic traffic is not an independent official-client replay or a reproduction of every Wurst mode.

The JAR contains no test probes, worlds, logs, player data or bundled Netty dependency.

SHA-256: `156c4be88cbe7fe780d1a1c5b850976a12d7662cb496667fdca09426b696410f`
