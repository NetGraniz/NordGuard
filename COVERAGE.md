# Wurst coverage map

NordGuard 0.5.0-rc.8, Minecraft 26.2. This is a scope map, not a claim that every listed client feature has been blocked. Packet freshness can defer stationary owner samples; the timeline does not add new enforced cheat classifiers. Horizontal catch-up allowance is bounded and comes from owner ticks, not client packet counts.

The final rc.8 native-client matrix detects and corrects Flight, SpeedHack, Spider and Jesus with the installed Wurst 7.56 client at three delay profiles on the tested Paper and Folia builds. Short default-setting reproductions are not a bypass audit. The relay used in earlier runs could reorder TCP fragments, so those latency results are historical observations rather than reliable FIFO-network evidence. The corrected fixture has a byte-order regression; its passing narrow matrix does not validate every terrain, transition or production CORRECT mode. Broader stage-4 validation remains open. See [TESTING.md](TESTING.md).

The feature catalog was checked against the [official Wurst source tree at f98551a](https://github.com/Wurst-Imperium/Wurst7/tree/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks). The movement implementations linked below were inspected to design synthetic cases. Other rows classify scope; they are not individual source audits or exploit reproductions. No Wurst code is bundled or copied.

**Implemented** means a server-side check exists for the specified behavior. It does not mean every setting, terrain combination or bypass is covered. **Partial** means checks cover only some excessive actions, not the whole feature. All release defaults are OBSERVE. Movement setbacks need a previously verified return position; attack/block cancellations do not.

## Implemented movement behavior

| Wurst feature | NordGuard check | Scope and limits |
| --- | --- | --- |
| [SpeedHack](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/SpeedHackHack.java) | SPEED | Sustained ordinary-terrain speed, including short micro-hops. Jump momentum is granted only for a plausible jump. Not a full friction or packet simulator. |
| [Spider](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/SpiderHack.java) | SPIDER | Repeated upward wall movement without normal gravity deceleration; does not wait for a full jump's ascent allowance. |
| [Flight](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/FlightHack.java) | FLIGHT | Unsupported hover and sustained vertical-physics deviations. Grace windows and authorized flight remain exempt. |
| CreativeFlight | FLIGHT | Same unsupported movement evidence, not detection of a client toggle. Server-authorized flight is exempt. |
| [Glide](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/GlideHack.java) | FLIGHT | Sustained artificially slow falling outside the normal falling curve. Actual elytra gliding and Slow Falling remain exempt. |
| [Jetpack](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/JetpackHack.java) | FLIGHT / HIGHJUMP | Repeated unsupported ascent; no dedicated Jetpack classifier. |
| HighJump | HIGHJUMP | Repeated excessive upward displacement. Isolated or packet-interleaved jumps can be missed. |
| [Step](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/StepHack.java) | HIGHJUMP | Repeated sampled steps above the server attribute; intermediate packets are not reconstructed. |
| [Jesus](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/JesusHack.java) | WATERWALK | Sustained unsupported position at a source water/lava surface, including small vertical oscillations. Real swimming, flowing liquids and solid support are outside this check. |
| [FastLadder](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/FastLadderHack.java) | CLIMB | Sustained ascent above 0.24 blocks per sampled tick while the server reports climbing. Ordinary 0.2 ascent has a separate test. |
| [NoWeb](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/NoWebHack.java) | NOWEB | Excessive horizontal or vertical movement while the body intersects a cobweb. Weaving and plugin-cancelled cobweb interactions defer checks. |
| NoFall | NOFALL | Missing base fall damage after an observed landing on ordinary solid terrain. Native damage processing and plugin overrides remain authoritative. |
| [NoSlowdown](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/mixin/noslowdown/LocalPlayerMixin.java) | NOSLOW | Sustained excessive grounded speed during stable server-observed item use. Reads the actual USE_EFFECTS multiplier, allows settling and accepted impulses; quick use/release loops are not validated. |

## Not fully covered by this build

| Feature | Status | Missing work or reason |
| --- | --- | --- |
| Timer | Partial | Excessive sampled movement can trigger SPEED. TickEnd rate is now recorded against a monotonic burst budget, but it is diagnostic only and is not a validated Timer classifier. |
| Blink | Partial | Large sampled displacements can accumulate speed evidence; bounded packet history and prefix acknowledgements are not a replicated, latency-compensated client world. |
| TpAura | Partial | Movement, Reach and WallHit check excessive actions. The new packet observer does not correlate combat with replicated client state. |
| NoClip | Partial | Near-horizontal sampled paths through full cubes, clear endpoints, normal-height bodies and 0.8–4 block displacement. Partial shapes and smaller/vertical steps are not covered. Shared scan budget can defer. |
| AntiKnockback | Not implemented | Server impulses are allowed, but acceptance of knockback is not required. |
| AntiEntityPush | Not implemented | Entity pushes are not reconstructed. |
| AntiWaterPush | Not implemented | Flow and current forces are not modeled. |
| Dolphin | Not implemented | Submerged movement is deferred. |
| Fish | Not implemented | Submerged movement is deferred. |
| AutoSwim | Not a standalone violation | Automatic sprint input is not proof of impossible swimming. |
| BoatFly | Not implemented | Vehicles are exempt. |
| ExtraElytra | Not implemented | Elytra gliding is exempt. |
| NoLevitation | Not implemented | Levitation is exempt. |
| SnowShoe | Not implemented | Powder snow is exempt. |
| Sneak | Not implemented | Sneaking slowdown and edge behavior need their own model. |
| InvWalk | Not implemented | Inventory-open input restrictions are not validated. |
| [BunnyHop](https://github.com/Wurst-Imperium/Wurst7/blob/f98551a3bfab97a1e70c340f91b334976c7fc4c2/src/main/java/net/wurstclient/hacks/BunnyHopHack.java) | Not a standalone violation | Automatic ordinary jumping does not by itself exceed legal movement. Excess speed is checked separately. |
| AutoSprint | Not a standalone violation | Sprint automation alone does not prove impossible movement. |
| AutoWalk | Not a standalone violation | Ordinary automatic walking has no unique movement signature. |
| Parkour | Not a standalone violation | Legal jumps are allowed, whether timed manually or automatically. |
| SafeWalk | Not implemented | Legal edge movement is not sufficient evidence of a cheat. |
| Killaura | Partial | Reach, approximate WallHit and AttackRate can reject excessive actions. Legal-looking aim and attack automation are not detected. |
| MultiAura | Partial | Shared per-attacker action budget, distance and sampled obstruction. No multi-target classifier. |
| Reach | Implemented, bounded | Server/item interaction range plus margin; fresh player snapshots, not full lag reconstruction. Block interaction range is checked separately. |
| Criticals | Not implemented | No critical-hit validation. |
| CrystalAura | Not implemented | No crystal placement/attack module. |
| FastBreak | Partial | Observed mining starts, native speed/early-stop threshold, permissive tool changes and lag compensation. Missing starts defer; no full dig-packet model. |
| Nuker | Partial | BlockReach, FastBreak and configurable BreakRate reject excessive actions, not all automated legal mining. No block line-of-sight check. |
| SpeedNuker | Partial | Same block reach, observed mining and rate limits. |
| Excavator | Not implemented | No automated mining classifier. |
| FastPlace | Partial | Configurable PlaceRate with one second of burst credit, plus BlockReach. No support/rotation classifier. |
| AirPlace | Not implemented | No placement-support validation. |
| ScaffoldWalk | Not implemented | No placement/rotation correlation. |
| AutoTotem | Not implemented | No inventory automation module. |
| AutoArmor | Not implemented | No inventory automation module. |
| XRay | Outside movement scope | Ore exposure needs server-side obfuscation; this plugin does not hide blocks. |
| CaveFinder | Outside movement scope | Information exposure needs server-side controls. |
| ChestEsp | Outside movement scope | Requires controlling information sent to clients. |
| PlayerEsp | Outside movement scope | Requires visibility/information controls, not movement limits. |
| Freecam | Not reliably identifiable here | A separate client camera need not move the server player. Impossible interactions would need their own checks. |
| CameraNoClip | Not reliably identifiable here | Camera rendering does not prove a server-side movement violation. |
| Fullbright | Not reliably identifiable here | Client brightness has no movement signature. |
| NoFog | Not reliably identifiable here | Client rendering option. |
| NoWeather | Not reliably identifiable here | Client rendering option. |
| NameTags | Not reliably identifiable here | Client rendering of already available information. |

## Validation boundary

See [TESTING.md](TESTING.md) for the exact tests executed. Constructed packets are not a running Wurst client. A passed synthetic case is not proof that every setting of the corresponding feature is blocked.

Combat and block histories are separate from the movement model. WallHit and NoClip share a hard spatial budget; saturated budgets deliberately skip checks. Inventory automation, complete combat prediction and vehicle/elytra physics remain outside this build. Adding feature names or lowering every movement threshold would not implement those protections and would increase false positives.
