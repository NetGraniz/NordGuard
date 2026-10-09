# Test record

Date: 2026-10-09. Release candidate: 0.2.0. Windows 11, Oracle JDK 25.

## Unit tests

`mvn -B -ntp clean verify`: 43 tests passed, no failures or skipped tests. GitHub Actions also passed the source build and all 43 tests.

Coverage includes ordinary sprint jumps, sustained hover, wall ascent, sustained speed, isolated movement bursts, repeated excessive ascent, observed landing distance, exemptions, teleport resets, speed attributes, disabled checks, fall-damage accounting, invalid policy limits and time-conversion overflow. Post-reset anchor eligibility tests ensure an unchecked first position cannot count as clean movement. The native teleport sequence increment and wrap are tested separately.

New cases cover micro-hop speed below the previous allowance, repeated ordinary sprint-jump momentum, early Spider evidence, ordinary jumps against walls, artificial slow falling, source-surface oscillations, brief surface crossings, ordinary/fast ladder ascent, ordinary/excessive cobweb movement, medium transitions, supported ladder-bottom anchors, zero-gravity attributes, item-use slowdown and custom full-speed item-use components. A migration test preserves existing modes while missing new check entries default to OBSERVE.

## Reproduced 0.1.0 regression

The retained original release JAR was tested with immediate repeated flight after its first successful setback. The first correction passed; the second flight attempt failed to receive another correction within five seconds. This is a failing regression, not a passing cheat-coverage test. Version 0.1.1 changes anchor retention and the settling period to address it.

The workload case runs 600 independent movement models through 1,200 frames each: 720,000 samples. It excludes block queries, native state reads, entity schedulers, packets, other plugins and real players. It is not evidence of capacity at 600 online players.

## Isolated runtime checks

Test runtimes use Minecraft 26.2 and a fresh flat world, with one loopback-only Mineflayer fixture account:

- Folia 26.2-7, commit `14b7fee`, API `26.2.build.7-beta`.
- Paper 26.2-132, commit `19ebc4a`, API `26.2.build.132-stable`.

Both runtimes passed startup, console status, valid reload, rejection of an invalid reload while retaining the old policy, block-shape support and clearance, non-OP permission defaults, per-player scheduling, native FALL damage, preservation of cancelled FALL events, partial native damage recovery, hover detection, ordinary fall-damage non-duplication and recovery after injected fall-distance suppression. No region-ownership or NordGuard internal-check errors appeared in those scenarios.

Both runtimes also passed an enabled Flight correction and two immediate repeated flight attempts. External teleport tests verify that the old return origin is invalidated and the next correction uses the new supported position. Ordinary walking caused no correction. Sustained constructed ground-speed attempts received three NordGuard corrections without moving the saved return point forward.

Each platform passed all 33 runtime assertions and stopped cleanly. This includes 0.5-block micro-hop speed, ordinary client-physics sprint jumps, constructed ordinary climbing/web/submerged movement, and corrections for Spider, WaterWalk, Climb, NoWeb and NoSlow. The tested Spider attempt reached 0.8 blocks on each final platform run before return; this is an observed fixture result, not a universal maximum under latency or low TPS.

Shield tests verified stable server-side item use. Ordinary slowed movement and a custom USE_EFFECTS multiplier of 1 did not flag NOSLOW. Excessive movement with an ordinary shield triggered a completed NordGuard correction. These cases do not validate every item, release/reuse sequence or airborne item use.

An earlier Folia test read the correction counter before the asynchronous entity-scheduled completion ran. The runner now waits up to three seconds for completion and still requires both NordGuard evidence and its own correction counter; a vanilla position packet alone cannot pass the test. The full Folia suite passed after that test fix. Earlier terrain fixtures also exposed vanilla collision corrections; they were corrected and are not counted as anti-cheat successes.

The same 35,976-byte JAR was tested on both platforms. SHA-256: `9c4bad419e744dc0c1eb02f3f5bb5d069d554e4fd76d2e9504832a5d28cfc158`.

The tested Folia asynchronous teleport did not advance the event-driven origin revision without the native-sequence fallback. The final build detected that server-issued teleport and passed the origin regression. Server teleport sequence reads run on the entity's region thread.

The suppression case deliberately changes the synthetic player's server-side fall distance through GuardProbe. It tests recovery, not the existence of a Wurst exploit. Cheat-like runtime cases use constructed movement packets, not a running Wurst client. HighJump and Glide have pure model tests, not end-to-end cheat-client tests. Lava surfaces, Weaving, cancelled cobweb interactions and flowing-liquid edge cases still need runtime validation.

GuardProbe and generated test worlds are not release contents. No production worlds, accounts, configurations or databases were used or modified.

## Remaining validation

- Sustained high-load tests with world queries and distributed Folia regions.
- Normal play across real terrain, knockback, enchantments, movement attributes and interactions with production plugins.
- Actual cheat clients, translated clients and subtle packet-timing patterns.
- Damage cooldowns and partial early damage during a real fall; the native bridge deliberately respects normal damage immunity.

All checks ship in OBSERVE mode. Correction is experimental; there are no automatic kicks or bans. Passing these tests does not establish complete cheat coverage or absence of false positives.
