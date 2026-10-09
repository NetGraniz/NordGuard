# Test record

Date: 2026-10-09. Release candidate: 0.1.1. Windows 11, Oracle JDK 25.

## Unit tests

`mvn -B -ntp clean verify`: 22 tests passed, no failures or skipped tests.

Coverage includes ordinary sprint jumps, sustained hover, wall ascent, sustained speed, isolated movement bursts, repeated excessive ascent, observed landing distance, exemptions, teleport resets, speed attributes, disabled checks, fall-damage accounting, invalid policy limits and time-conversion overflow. Post-reset anchor eligibility tests ensure an unchecked first position cannot count as clean movement. The native teleport sequence increment and wrap are tested separately.

## Reproduced 0.1.0 regression

The retained original release JAR was tested with immediate repeated flight after its first successful setback. The first correction passed; the second flight attempt failed to receive another correction within five seconds. This is a failing regression, not a passing cheat-coverage test. Version 0.1.1 changes anchor retention and the settling period to address it.

The workload case runs 600 independent movement models through 1,200 frames each: 720,000 samples. It excludes block queries, native state reads, entity schedulers, packets, other plugins and real players. It is not evidence of capacity at 600 online players.

## Isolated runtime checks

Test runtimes use Minecraft 26.2 and a fresh flat world, with one loopback-only Mineflayer fixture account:

- Folia 26.2-7, commit `14b7fee`, API `26.2.build.7-beta`.
- Paper 26.2-132, commit `19ebc4a`, API `26.2.build.132-stable`.

Both runtimes passed startup, console status, valid reload, rejection of an invalid reload while retaining the old policy, block-shape support and clearance, non-OP permission defaults, per-player scheduling, native FALL damage, preservation of cancelled FALL events, partial native damage recovery, hover detection, ordinary fall-damage non-duplication and recovery after injected fall-distance suppression. No region-ownership or NordGuard internal-check errors appeared in those scenarios.

Both runtimes also passed an enabled Flight correction and two immediate repeated flight attempts. External teleport tests verify that the old return origin is invalidated and the next correction uses the new supported position. Ordinary walking caused no correction. Sustained constructed ground-speed attempts received three NordGuard corrections without moving the saved return point forward. Each platform passed all 21 runtime assertions and stopped cleanly; the tested JAR SHA-256 was identical on both platforms.

The tested Folia asynchronous teleport did not advance the event-driven origin revision without the native-sequence fallback. The final build detected that server-issued teleport and passed the origin regression. Server teleport sequence reads run on the entity's region thread.

The suppression case deliberately changes the synthetic player's server-side fall distance through GuardProbe. It tests recovery, not the existence of a Wurst exploit. Flight and Speed runtime cases use constructed movement packets, not a running Wurst client. Spider and HighJump are covered by pure model tests, not end-to-end cheat-client tests.

GuardProbe and generated test worlds are not release contents. No production worlds, accounts, configurations or databases were used or modified.

## Remaining validation

- Sustained high-load tests with world queries and distributed Folia regions.
- Normal play across real terrain, knockback, enchantments, movement attributes and interactions with production plugins.
- Actual cheat clients, translated clients and subtle packet-timing patterns.
- Damage cooldowns and partial early damage during a real fall; the native bridge deliberately respects normal damage immunity.

All checks ship in OBSERVE mode. Correction is experimental; there are no automatic kicks or bans. Passing these tests does not establish complete cheat coverage or absence of false positives.
