# NordGuard

Movement and NoFall checks for Minecraft 26.2 on Paper and Folia. Version 0.2.0 is an experimental, observation-first build, not a complete anti-cheat.

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

NoFall prevents avoidance of fall damage; it does not disable normal fall damage. It reconstructs a fall from sampled height and actual block support, not the client's on-ground flag. It accounts for base FALL damage already observed, so partial early damage does not exempt an entire fall. Cancellation or modification of base damage by another plugin suppresses recovery for that fall. NordGuard does not override that decision or set health directly.

NoFall respects the fall-damage game rule. Recovery also goes through native damage cooldowns, armor and enchantments; it does not force health loss through invulnerability. The version-gated native bridge must resolve before the plugin enables. This build rejects Minecraft versions other than 26.2.

The native teleport bridge reads the server-issued teleport sequence on the player's region thread. This also detects external asynchronous teleports on the tested Folia build when a Bukkit teleport event is absent. Client movement flags do not set that sequence. Both native bridges need revalidation before supporting another Minecraft version.

## Installation and configuration

Requires Java 25. Put one release JAR in `plugins` while the server is stopped. No client mod, database, packet library or external service is required. Configuration: `plugins/NordGuard/config.yml`.

All nine checks default to `OBSERVE`. There are no automatic bans or kicks. Test legitimate gameplay on the same platform before enabling corrections.

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
```

Each mode accepts `OFF`, `OBSERVE` or `CORRECT`. Observation records evidence without changing position or health. Correction permits movement setbacks or native NoFall damage. A setback needs a previous clean supported position that remains loaded, region-owned and clear; otherwise the plugin reports without forcing a teleport.

`movement.violation-buffer` controls accumulated movement evidence. Spider uses at most three evidence samples; WaterWalk uses at least ten to allow brief surface crossings. `horizontal-margin` is added once to the speed burst budget, not to every tick's speed allowance. `vertical-margin` remains a vertical tolerance; Spider scales it when comparing gravity deceleration. `burst-ticks` bounds the horizontal allowance for brief bursts. Initial values are starting points, not calibrated guarantees.

Speed uses the server movement-speed attribute, walk speed and accepted velocity impulses. A plausible normal jump receives a decaying horizontal momentum allowance; tiny client hops do not. These are bounded envelopes, not a complete simulation of friction, packets or client physics.

Existing schema-version 1 configurations remain readable. Missing `waterwalk`, `climb`, `noweb` and `noslow` modes default to OBSERVE; the plugin does not silently enable corrections or overwrite your settings. Add those four entries explicitly when selecting their modes. The meaning of `horizontal-margin` changed in 0.2.0; retest your speed settings before enabling corrections.

NoSlow starts after ten server-observed item-use ticks and six consecutive supported movement samples. It respects a custom USE_EFFECTS speed multiplier, skips airborne movement and defers during accepted velocity impulses. Quick use/release loops and item-use packet ordering are not covered. The component and its 0.2 fallback were checked against the tested 26.2 runtime.

Join and transition grace values use player scheduler ticks. Long sampling gaps clear history. Accepted velocity events briefly reset history and add a capped, decaying impulse allowance. Unrecognized or special environments defer checks.

Reload validates a complete policy before publication. Invalid reloads retain the previous policy; successful reloads reset histories on the next player sample.

Temporary history resets keep the last clean supported return position. The first sample after a reset cannot replace it: the model needs displacement evidence before accepting a new clean position. External teleports, respawns, world changes and game-mode changes discard it. A successful NordGuard setback keeps its validated destination and uses a two-tick settling period instead of the ordinary transition grace. Immediate repeated movement violations can therefore trigger another setback without waiting for a new ground sample. Evidence buffers still apply; corrections are not instantaneous packet rejection.

## Commands and permissions

| Command / permission | Purpose | Default |
| --- | --- | --- |
| `/nordguard status` | Modes, sessions, violation samples, corrections and mean sample time. Requires `nordguard.admin`. | Console / OP |
| `/nordguard reload` | Validate and reload configuration. Requires `nordguard.admin`. | Console / OP |
| `/nordguard alerts` | Toggle personal alerts. Requires `nordguard.admin` and `nordguard.alerts`. | Explicit grant |
| `nordguard.admin` | Administration commands. | OP |
| `nordguard.alerts` | Receive subscribed alerts; rechecked before delivery. | false |
| `nordguard.bypass` | Skip all checks. Do not grant by default. | false |

Uses standard Bukkit permissions, including NordPerms. Administration does not grant a bypass. Non-OP moderators need explicit grants. No player identities belong in the repository.

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

One entity-scheduled task samples each online player at most once per server tick. Ordinary stationary supported players use a reduced probe frequency. Movement checks share one bounded history.

The environment probe inspects at most 36 nearby block positions and checks loaded chunks and region ownership before access. It never requests chunk loading. Cross-region setbacks use `teleportAsync` with entity-scheduled completion. Live-world reads do not run on background workers.

Output is throttled per player and check. Violation counters count samples, not confirmed cheaters. Mean sampling time includes idle and deferred samples, not full-server cost or latency percentiles. No telemetry, update checks, external requests or per-move disk writes are included. Console messages may contain player names and observed fall distances.

## Coverage limits

- Tick-sampled protection is not full packet-order validation or a complete physics simulator. Actions between samples can be missed.
- Creative, Spectator, authorized flight, vehicles, gliding, riptide, levitation and slow falling remain outside the model. Elytra-speed and vehicle cheats are not covered.
- Swimming/submerged movement, flowing liquids, waterlogged blocks, ice, slime, honey, beds, hay, powder snow, berry bushes, scaffolding, soul sand and nearby pistons conservatively defer checks. These exemptions leave gaps. Source liquid surfaces, climbing and cobwebs now have separate checks instead of a blanket exemption.
- NoFall covers observed falls on ordinary supported terrain. Mid-tick rescue mechanics, damage cooldowns, plugin modifications and incomplete event history affect evidence. Correction remains experimental.
- HighJump/Step targets repeated violations, not every isolated jump. Fine speed advantages, collision phasing and arbitrary client timing are not fully covered.
- Combat, Reach, Kill Aura, FastBreak, Nuker, authentication and ore obfuscation are not included.
- Translated or older clients need separate testing. Initial runtime checks use a 26.2 client.

Target deployment: 600 players. That capacity is not validated. Bounded work and small state are design choices, not a measured TPS guarantee.

See [COVERAGE.md](COVERAGE.md) for the Wurst feature map, including implemented behaviors, partial coverage and missing modules. Listing a client feature there does not mean it is blocked.

## Build and tests

```text
mvn -B -ntp clean verify
```

Requires Maven and JDK 25. Output: `target/NordGuard-0.2.0.jar`. The provided Paper API is not bundled.

Unit tests cover ordinary jumps, hover, wall ascent, speed, bursts, excessive ascent, landing distance, exemptions, resets, attributes, disabled checks and policy limits. A synthetic workload exercises 600 model instances; it excludes world queries, networking and scheduling and is not a 600-player load test.

See [test-support/README.md](test-support/README.md) for isolated runtime checks and [TESTING.md](TESTING.md) for results. Never deploy GuardProbe to production; it is excluded from the release JAR.

## References

Architecture research: [Grim](https://github.com/GrimAnticheat/Grim), [NoCheatPlus](https://github.com/Updated-NoCheatPlus/NoCheatPlus), [Folia support](https://docs.papermc.io/paper/dev/folia-support/). Wurst Flight and Spider informed synthetic test behavior. No third-party project source was copied into NordGuard.
