# Changelog

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
