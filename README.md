# NordGuard

Movement and NoFall checks for Minecraft 26.2 on Paper and Folia. Version 0.1.0 is an experimental, observation-first build, not a complete anti-cheat.

## Checks

| Check | Initial scope |
| --- | --- |
| Flight / Hover | Sustained unsupported hovering or ascent outside a conservative movement envelope. |
| Spider | Sustained upward movement against collision shapes after the normal ascent allowance. |
| Speed | Sustained horizontal displacement above an attribute-adjusted allowance, with a bounded burst budget. |
| HighJump / Step | Repeated excessive upward displacement or grounded step height. |
| NoFall | Insufficient base fall damage after an observed geometrical landing. Correction uses native fall-damage processing. |

NoFall prevents avoidance of fall damage; it does not disable normal fall damage. It reconstructs a fall from sampled height and actual block support, not the client's on-ground flag. It accounts for base FALL damage already observed, so partial early damage does not exempt an entire fall. Cancellation or modification of base damage by another plugin suppresses recovery for that fall. NordGuard does not override that decision or set health directly.

NoFall respects the fall-damage game rule. Recovery also goes through native damage cooldowns, armor and enchantments; it does not force health loss through invulnerability. The version-gated native bridge must resolve before the plugin enables. This build rejects Minecraft versions other than 26.2.

## Installation and configuration

Requires Java 25. Put one release JAR in `plugins` while the server is stopped. No client mod, database, packet library or external service is required. Configuration: `plugins/NordGuard/config.yml`.

All five checks default to `OBSERVE`. There are no automatic bans or kicks. Test legitimate gameplay on the same platform before enabling corrections.

```yaml
checks:
  flight: OBSERVE
  spider: OBSERVE
  speed: OBSERVE
  highjump: OBSERVE
  nofall: OBSERVE
```

Each mode accepts `OFF`, `OBSERVE` or `CORRECT`. Observation records evidence without changing position or health. Correction permits movement setbacks or native NoFall damage. A setback needs a previous clean supported position that remains loaded, region-owned and clear; otherwise the plugin reports without forcing a teleport.

`movement.violation-buffer` controls accumulated movement evidence. Horizontal and vertical margins are tolerances in blocks per sample, not universal speed limits. `burst-ticks` bounds the horizontal allowance for brief bursts. Initial values are conservative starting points, not calibrated guarantees.

Join and transition grace values use player scheduler ticks. Long sampling gaps clear history. Accepted velocity events briefly reset history and add a capped, decaying impulse allowance. Unrecognized or special environments defer checks.

Reload validates a complete policy before publication. Invalid reloads retain the previous policy; successful reloads reset histories on the next player sample.

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
- Creative, Spectator, authorized flight, vehicles, gliding, riptide, climbing, levitation and slow falling are outside the initial model. Elytra-speed and vehicle cheats are not covered.
- Liquids, waterlogged blocks, ice, slime, honey, beds, hay, webs, powder snow, berry bushes, scaffolding, soul sand and nearby pistons conservatively defer checks. These exemptions leave gaps.
- NoFall covers observed falls on ordinary supported terrain. Mid-tick rescue mechanics, damage cooldowns, plugin modifications and incomplete event history affect evidence. Correction remains experimental.
- HighJump/Step targets repeated violations, not every isolated jump. Fine speed advantages, collision phasing and arbitrary client timing are not fully covered.
- Combat, Reach, Kill Aura, FastBreak, Nuker, authentication and ore obfuscation are not included.
- Translated or older clients need separate testing. Initial runtime checks use a 26.2 client.

Target deployment: 600 players. That capacity is not validated. Bounded work and small state are design choices, not a measured TPS guarantee.

## Build and tests

```text
mvn -B -ntp clean verify
```

Requires Maven and JDK 25. Output: `target/NordGuard-0.1.0.jar`. The provided Paper API is not bundled.

Unit tests cover ordinary jumps, hover, wall ascent, speed, bursts, excessive ascent, landing distance, exemptions, resets, attributes, disabled checks and policy limits. A synthetic workload exercises 600 model instances; it excludes world queries, networking and scheduling and is not a 600-player load test.

See [test-support/README.md](test-support/README.md) for isolated runtime checks and [TESTING.md](TESTING.md) for results. Never deploy GuardProbe to production; it is excluded from the release JAR.

## References

Architecture research: [Grim](https://github.com/GrimAnticheat/Grim), [NoCheatPlus](https://github.com/Updated-NoCheatPlus/NoCheatPlus), [Folia support](https://docs.papermc.io/paper/dev/folia-support/). Wurst Flight and Spider informed synthetic test behavior. No third-party project source was copied into NordGuard.
