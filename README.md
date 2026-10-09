# NordGuard

Bounded movement, combat and block checks for Minecraft 26.2 on Paper and Folia. Version 0.4.0 adds an experimental packet timeline. It checks impossible or excessive server-visible actions, not whether a particular client modification is installed.

## Checks

| Check | Initial scope |
| --- | --- |
| Flight / Hover | Sustained unsupported hovering or ascent outside a conservative movement envelope. |
| Spider | Repeated upward wall movement inconsistent with gravity; three evidence samples at the default buffer, without the old ascent delay. |
| Speed | Sustained horizontal displacement above an attribute-adjusted allowance, with a bounded burst budget. |
| HighJump / Step | Repeated excessive upward displacement or grounded step height. |
| WaterWalk | Sustained unsupported position at a source water/lava surface, including small vertical oscillations. |
| Climb | Repeated ascent faster than 0.24 blocks per sampled tick while the server reports climbing. |
| NoWeb | Excess movement while the player's body intersects a cobweb; Weaving and plugin-cancelled cobweb interactions defer checks. |
| NoSlow | Sustained excessive grounded speed during server-observed item use; reads the item's USE_EFFECTS speed multiplier. |
| NoFall | Insufficient base fall damage after an observed geometrical landing. Correction uses native fall-damage processing. |
| NoClip | Near-horizontal sampled paths crossing a full solid cube, with clear endpoints. Only bodies at least 1.5 blocks high, displacement above 0.8 and below 4 blocks, and vertical change below 0.05 are checked. |
| Reach | Eye-to-target-box distance beyond the server range, including the actual ATTACK_RANGE weapon component. Recent player snapshots provide a small lag allowance. |
| WallHit | All ten sampled sight lines cross full cubes in a bounded, region-owned area. Matching blocked geometry must persist for at least 200 ms. An approximation, not complete visibility reconstruction. |
| AttackRate | A configurable action budget; 40 attacks/second and one second of burst credit by default. Fast legal clicking is not classified as Kill Aura. |
| BlockReach | Breaking or placing beyond the server block-interaction attribute plus a margin. |
| FastBreak | Premature destruction with an observed mining start. Uses native break speed, the 26.2 early-stop threshold and wall-clock lag compensation. |
| BreakRate / PlaceRate | Configurable action budgets; 25 breaks/second and 20 placements/second with one second of burst credit. Server limits, not automation classifiers. |

NoFall prevents avoidance of fall damage; it does not disable normal fall damage. It reconstructs a fall from sampled height and actual block support, not the client's on-ground flag. It accounts for base FALL damage already observed, so partial early damage does not exempt an entire fall. Cancellation or modification of base damage by another plugin suppresses recovery for that fall. NordGuard does not override that decision or set health directly.

NoFall respects the fall-damage game rule. Recovery also goes through native damage cooldowns, armor and enchantments; it does not force health loss through invulnerability. The version-gated native bridge must resolve before the plugin enables. This build rejects Minecraft versions other than 26.2.

The native teleport bridge reads the server-issued teleport sequence on the player's region thread. This also detects external asynchronous teleports on the tested Folia build when a Bukkit teleport event is absent. Client movement flags do not set that sequence. Both native bridges need revalidation before supporting another Minecraft version.

## Installation and configuration

Requires Java 25. Put one release JAR in `plugins` while the server is stopped. No client mod, database, packet library or external service is required. Configuration: `plugins/NordGuard/config.yml`.

All 17 checks default to `OBSERVE`. There are no automatic bans or kicks. Test legitimate gameplay on the same platform before enabling corrections.

```yaml
checks:
  flight: OBSERVE
  spider: OBSERVE
  speed: OBSERVE
  highjump: OBSERVE
  waterwalk: OBSERVE
  climb: OBSERVE
  noweb: OBSERVE
  noslow: OBSERVE
  nofall: OBSERVE
  noclip: OBSERVE
  reach: OBSERVE
  wallhit: OBSERVE
  attackrate: OBSERVE
  blockreach: OBSERVE
  fastbreak: OBSERVE
  breakrate: OBSERVE
  placerate: OBSERVE
```

Each mode accepts `OFF`, `OBSERVE` or `CORRECT`. Observation records evidence without changing position, health or event cancellation. Correction permits movement setbacks, native NoFall damage or cancellation of the offending attack/break/place event. A setback needs a previous clean supported position that remains loaded, region-owned and clear; otherwise the plugin reports without forcing a teleport. Existing cancellations from other plugins are respected.

### Action limits

```yaml
actions:
  reach-margin: 0.35
  history-ms: 200
  max-blocks-per-scan: 128
  spatial-checks-per-tick: 2
  spatial-blocks-per-second: 20000
  attacks-per-second: 40
  breaks-per-second: 25
  places-per-second: 20
```

Reach reads the native interaction attribute when the main-hand item has no ATTACK_RANGE component; otherwise the component's maximum reach and hitbox margin apply. Creative and Spectator are exempt. Authorization for flight does not exempt survival combat or block actions. `nordguard.bypass` does.

Player snapshots publish at most ten times per second as one immutable pair. The pair holds the latest and preceding position, not a complete movement timeline. `history-ms` caps the permitted age of the preceding snapshot; values above 200 do not create additional history. The effective allowance also depends on reported ping. `0` disables preceding-snapshot allowance. Live targets owned by the current region use their current box; foreign-region players use fresh immutable snapshots. Missing, stale or different-world snapshots defer spatial checks. Teleports clear history; native teleport sequences also invalidate owned-target history between samples.

WallHit and NoClip share a server-wide spatial cell budget, split into 50 ms windows. The default allows at most 1,000 requested cells per window, across all players and regions. Each scan also has its own cell limit; WallHit has a per-attacker scan limit. Budget exhaustion skips the scan. This deliberately sacrifices coverage under saturation instead of adding unbounded tick work. First-come allocation is not a fairness guarantee. Cheap distance and action-rate checks continue.

FastBreak tracks one active block per player. Repeated starts replace the timeline, matching the native game mode. The historical maximum break speed makes tool or effect changes permissive. Eligibility includes elapsed monotonic time because 26.2 mining uses lag compensation. A two-tick allowance and 0.05 progress margin accommodate event order; the native early-stop threshold is 0.7. Plugin-authorized instant breaks are allowed. A break without a known start is not proof of FastBreak and is not rejected by that check. Rate limits can still apply to instant or plugin-triggered actions.

Rate budgets refill with monotonic elapsed time, not packet count or server TPS. One second of burst credit means a short burst can exceed the per-second setting. Pick limits compatible with your own instant-mining, building and combat mechanics before enabling CORRECT.

`movement.violation-buffer` controls accumulated movement evidence. Spider uses at most three evidence samples; WaterWalk uses at least ten to allow brief surface crossings. `horizontal-margin` is added once to the speed burst budget, not to every tick's speed allowance. `vertical-margin` remains a vertical tolerance; Spider scales it when comparing gravity deceleration. `burst-ticks` bounds the horizontal allowance for brief bursts. Initial values are starting points, not calibrated guarantees.

Speed uses the server movement-speed attribute, walk speed and accepted velocity impulses. A plausible normal jump receives a decaying horizontal momentum allowance; tiny client hops do not. These are bounded envelopes, not a complete simulation of friction, packets or client physics.

Existing schema-version 1 configurations remain readable. Missing modes default to OBSERVE and missing action limits use the defaults above; the plugin does not silently enable corrections or overwrite your settings. Add new entries explicitly when selecting their modes. The meaning of `horizontal-margin` changed in 0.2.0; retest your speed settings before enabling corrections.

NoSlow starts after ten server-observed item-use ticks and six consecutive supported movement samples. It respects a custom USE_EFFECTS speed multiplier, skips airborne movement and defers during accepted velocity impulses. Quick use/release loops and item-use packet ordering are not covered. The component and its 0.2 fallback were checked against the tested 26.2 runtime.

Join and transition grace values use player scheduler ticks. Long sampling gaps clear history. Accepted velocity events briefly reset history and add a capped, decaying impulse allowance. Unrecognized or special environments defer checks.

Reload validates a complete policy before publication. Invalid reloads retain the previous policy; successful reloads reset histories on the next player sample.

Temporary history resets keep the last clean supported return position. The first sample after a reset cannot replace it: the model needs displacement evidence before accepting a new clean position. External teleports, respawns, world changes and game-mode changes discard it. A successful NordGuard setback keeps its validated destination and uses a two-tick settling period instead of the ordinary transition grace. Immediate repeated movement violations can therefore trigger another setback without waiting for a new ground sample. Evidence buffers still apply; corrections are not instantaneous packet rejection.

## Commands and permissions

| Command / permission | Purpose | Default |
| --- | --- | --- |
| `/nordguard status` | Modes, sessions, evidence, corrections, sample/action timings and spatial budget counters. Requires `nordguard.admin`. | Console / OP |
| `/nordguard reload` | Validate and reload configuration. Requires `nordguard.admin`. | Console / OP |
| `/nordguard alerts` | Toggle personal alerts. Requires `nordguard.admin` and `nordguard.alerts`. | Explicit grant |
| `/nordguard inspect <player>` | Inspect bounded movement-packet history and client-processing barrier diagnostics for an online player. Requires `nordguard.admin`. | Console / OP |
| `nordguard.admin` | Administration commands. | OP |
| `nordguard.alerts` | Receive subscribed alerts; rechecked before delivery. | false |
| `nordguard.bypass` | Skip all checks. Do not grant by default. | false |

Uses standard Bukkit permissions, including NordPerms. Administration does not grant a bypass. Non-OP moderators need explicit grants. No player identities belong in the repository.

## Packet foundation (0.4.0)

`packets.enabled: true` installs a transparent, version-pinned channel observer. It retains primitives, not Minecraft packet objects, and never reads Bukkit worlds or players from a network callback. Each player has a 256-event inbox and a 64-movement history. The existing entity-scheduler task drains at most 128 entries per tick. Overflow discards the whole uncertain queued prefix over bounded drains; existing movement and action checks keep running.

Two Ping/Pong barriers per second measure client processing of the preceding outbound stream. At most four barriers remain pending, with a five-second timeout. The official 26.2 client handles this Ping on its game thread; keep-alive is not used for these measurements. Matching known IDs, ordered replies and timeouts bound the bookkeeping. Other plugins' Ping packets invalidate outstanding measurements; the last eight foreign IDs are avoided when choosing a probe. This is conservative coexistence, not a reserved or authenticated protocol namespace.

The timeline observes movement variants, persistent input, client TickEnd, self velocity, teleports and matching teleport confirmations. Outbound chunk/block changes, self attributes/effects/metadata, abilities and other transitions invalidate confidence. Nested or oversized bundles also invalidate it. Relative teleport coordinates are not treated as absolute positions. TickEnd is an untrusted claim; an independent monotonic rate budget records excess without awarding time from packet count. Its burst diagnostics are not grounds for correction or bans.

An acknowledged stream prefix does **not** reconstruct the client's world or prove that a modified client obeyed a packet. No packet-based correction is enabled in this build. Reach history and existing movement checks retain their previous behavior; this timeline does not silently replace them with complete latency compensation.

`OrdinaryPhysics` separately implements the verified 26.2 free-space arithmetic for ordinary digital input, sprint jumps, movement attributes, friction and air drag. It is unit tested but not wired into enforcement. Collision shapes, step-up, client-world replication, ambiguous in-flight state and candidate selection still need implementation and differential validation before it can become a movement predictor. Fluids, climbing, vehicles and special movement are outside this kernel's contract.

Packet collection can be disabled and re-enabled with configuration reload. Observer failures disable diagnostics for the affected session without suspending the existing checks. Quit, retired sessions and plugin disable remove only their own channel handler. Diagnostic output is requested by an administrator; there is no automatic per-packet logging or disk I/O. History and barrier storage use 15,696 bytes of primitive-array payload per session, excluding object headers, native channel state and shared bindings.

If NordCommands filters available commands, add `nordguard` to its allowed command labels. NordGuard still requires its own administration permission; making the label visible does not grant access.

Add these nodes to the existing NordPerms group permission maps, not as a second top-level `groups` section:

```yaml
# players.permissions
nordguard.admin: false
nordguard.alerts: false
nordguard.bypass: false
# moderators.permissions
nordguard.admin: true
nordguard.alerts: true
```

## Runtime design

One entity-scheduled task samples each online player at most once per server tick. Ordinary stationary supported players use a reduced probe frequency. Movement checks share one bounded history. Action checks reuse that task for snapshots and active mining; there is no additional per-player timer or player-pair search. Expensive scans run only for candidate actions or candidate wall crossings.

The environment probe inspects at most 36 nearby block positions and checks loaded chunks and region ownership before access. It never requests chunk loading. Cross-region setbacks use `teleportAsync` with entity-scheduled completion. Live-world reads do not run on background workers.

Alerts also share a global limit of one emitted message per 50 ms window, in addition to the per-player/check cooldown. Suppressed messages do not suppress evidence counters or corrections. With console alerts disabled and no subscribers, reporting avoids message construction and delivery work.

Output is throttled per player and check. Violation counters count samples, not confirmed cheaters. Mean sampling time includes idle/deferred samples, snapshot publication and mining updates. Mean/slowest action time covers attack, break and place handlers, not the entire server pipeline or mining-start handler. Corrections count completed setbacks, native fall recoveries and cancelled actions. Spatial cells count reserved scan cells, not necessarily every cell read before a defer. These counters do not establish a full-server latency percentile or capacity guarantee. No telemetry, update checks, external requests or per-move disk writes are included. Console messages may contain player names and observed fall distances.

## Coverage limits

- Tick-sampled protection is not full packet-order validation or a complete physics simulator. Actions between samples can be missed.
- Creative, Spectator, authorized flight, vehicles, gliding, riptide, levitation and slow falling remain outside the model. Elytra-speed and vehicle cheats are not covered.
- Swimming/submerged movement, flowing liquids, waterlogged blocks, ice, slime, honey, beds, hay, powder snow, berry bushes, scaffolding, soul sand and nearby pistons conservatively defer checks. These exemptions leave gaps. Source liquid surfaces, climbing and cobwebs now have separate checks instead of a blanket exemption.
- NoFall covers observed falls on ordinary supported terrain. Mid-tick rescue mechanics, damage cooldowns, plugin modifications and incomplete event history affect evidence. Correction remains experimental.
- HighJump/Step targets repeated violations, not every isolated jump. Fine speed advantages, collision phasing and arbitrary client timing are not fully covered.
- Combat checks cover excessive range, sampled obstruction and action budgets, not every Kill Aura mode, aim pattern or critical-hit exploit. Legal-looking automation can pass.
- NoClip covers only a narrow full-cube path case. Small steps through walls, partial shapes, crawl poses, large teleports and intermediate movement packets are not reconstructed.
- WallHit ignores partial shapes and defers around doors, trapdoors, pistons, slime or honey. Ten target points can miss a small exposed area; changed terrain and latency still require gameplay testing. New geometry receives a 200 ms settling window.
- Block checks do not validate placement support, rotation, every dig packet, inventory automation, authentication or ore obfuscation.
- Translated or older clients need separate testing. Initial runtime checks use a 26.2 client.

Target deployment: 600 players. That capacity is not validated. Bounded work and small state are design choices, not a measured TPS guarantee.

See [COVERAGE.md](COVERAGE.md) for the Wurst feature map, including implemented behaviors, partial coverage and missing modules. Listing a client feature there does not mean it is blocked.

## Build and tests

```text
mvn -B -ntp clean verify
```

Requires Maven and JDK 25. Output: `target/NordGuard-0.3.0.jar`. The provided Paper API is not bundled.

Unit tests cover ordinary jumps, hover, wall ascent, speed, bursts, excessive ascent, landing distance, exemptions, resets, attributes, disabled checks and policy limits. A synthetic workload exercises 600 model instances; it excludes world queries, networking and scheduling and is not a 600-player load test.

See [test-support/README.md](test-support/README.md) for isolated runtime checks and [TESTING.md](TESTING.md) for results. Never deploy GuardProbe to production; it is excluded from the release JAR.

## References

Architecture research: [Grim](https://github.com/GrimAnticheat/Grim), [NoCheatPlus](https://github.com/Updated-NoCheatPlus/NoCheatPlus), [Folia support](https://docs.papermc.io/paper/dev/folia-support/). Wurst Flight and Spider informed synthetic test behavior. No third-party project source was copied into NordGuard.
