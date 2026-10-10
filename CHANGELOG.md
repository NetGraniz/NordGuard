# Changelog

## 0.5.0-rc.6 — stage 4 transition corrections

- Recognize one, two or three cumulative ordinary jump steps before granting bounded sprint-jump momentum. Keep micro-hops and implausible high takeoffs outside that match.
- Reuse the existing bounded collision scan to find a nearby supporting floor without treating it as current ground contact. Recognize a missed landing/takeoff only after recent descent, a floor-crossing fall step and at least ten sampled movement ticks since the last recognized jump. Preserve unaccounted fall height.
- When the active packet observer received neither movement nor TickEnd and the airborne owner position is unchanged, defer vertical evidence while repaying existing speed debt. Grounded idle samples retain their normal return-anchor refresh. Changed positions and received stationary movement still get checked; no future speed credit is stored. Observer-disabled/unavailable sessions retain the existing owner-only fallback.
- Extend native traces and ordinary-case duration controls. Retry short Windows test-control file locks with a fixed limit; unexpected errors fail the fixture.
- Keep OBSERVE defaults. This remains an experimental candidate; passing narrow transition regressions does not close the full release gate.

## 0.5.0-rc.5 — stage 4 investigation

- Repay horizontal speed debt on skipped, unchanged grounded owner ticks without extra world queries, timers or banked future credit. Keep excessive-average-speed detection and untrusted reset origins covered by unit tests.
- Add a separate Fabric fixture that drives actual Minecraft 26.2 input and verifies installed Wurst Flight, SpeedHack, Spider and Jesus enablement on a loopback-only test connection. No client or test code is bundled in NordGuard.
- Record ordinary movement, per-check evidence and completed corrections under real TCP delay and ordered jitter. Split explicitly disabled/enabled Nagle buffering into labelled test profiles.
- Retain failing native-client regressions. Corrections under batched traffic are not stable; stage 4 and the final release gate remain open. Defaults remain OBSERVE.

## 0.4.0

- Add a bounded, observation-only 26.2 packet timeline with movement variants, persistent input, TickEnd, teleport confirmations and self velocity.
- Measure known ordered Ping/Pong client-processing barriers; invalidate confidence after outbound world/context updates, missing replies and queue overflow. Do not equate this with a replicated client world.
- Keep all network callbacks free of Bukkit state reads. Retain primitive fields in a fixed inbox and drain through the existing owner scheduler.
- Isolate optional observer failures from movement enforcement; remove channel handlers on quit, retired sessions and disable, including attachment races.
- Add `/nordguard inspect <player>` and reloadable `packets.enabled`. No per-packet logging, new punishment mode or automatic ban.
- Add a standalone 26.2 ordinary free-space physics kernel and unit tests. Collision and client-world prediction are not implemented by that kernel.
- Extend isolated runtime tests with an actual delayed TCP relay, barrier observation, owner-thread stalls and observer reload lifecycle checks.

## 0.3.0

- Add Reach and bounded WallHit checks before attack damage, respecting native interaction attributes and weapon ATTACK_RANGE components.
- Publish immutable player-history pairs on the owner scheduler; defer stale or unknown foreign-region targets.
- Add configurable attack, break and placement token budgets, plus BlockReach and observed-start FastBreak.
- Match mining eligibility to the native 0.7 early-stop threshold, wall-clock lag compensation, repeated starts and plugin instant-break overrides. Tool changes receive permissive progress.
- Add a narrow NoClip path check for near-horizontal crossings of full cubes, without advancing the return anchor after observed phasing.
- Share a hard spatial-cell budget across all regions. Saturation defers scans instead of queuing work. Newly blocked wall geometry gets 200 ms to settle.
- Bound alert emission globally and expose action timings and spatial-defer counters in status.
- Skip air earlier in environment probes and precompute the special-material set. No world-geometry cache or chunk loading.
- Extend isolated tests with action-event gates, real ordinary client attack/mining, NoClip history fault injection, scan safety and a bounded warm microbenchmark. Keep all new modes OBSERVE by default.

## 0.2.0

- Add WaterWalk, Climb, NoWeb and server-observed item-use NoSlow checks.
- Treat horizontal margin as burst credit rather than a per-tick allowance; grant jump momentum only for plausible jumps.
- Detect repeated Spider ascent earlier while keeping ordinary wall jumps legal.
- Expand pure and isolated runtime tests for movement media, item components and micro-hop speed.

## 0.1.1

- Preserve the last clean supported return position during temporary evidence resets.
- Require displacement evidence before the first post-reset position can replace the return point, preventing repeated ground-speed attempts from moving it forward.
- Keep the validated destination after a successful NordGuard setback; use two settling ticks rather than the configured external-transition grace.
- Discard the old return position on external teleports, respawns, world changes and game-mode changes. Ignore stale correction completions after a new origin transition.
- Track the server-issued teleport sequence as a fallback for external asynchronous teleports on Folia. Reject a correction completion if another server teleport has intervened.
- Add runtime regressions for immediate repeated flight, repeated speed corrections, external teleport handling and ordinary walking.
- Keep existing check modes, permissions and configuration schema. All checks still default to OBSERVE.

## 0.1.0

- Initial movement and NoFall preview for Minecraft 26.2 Paper and Folia.
