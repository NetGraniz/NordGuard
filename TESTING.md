# Test record

## 0.4.0

Date: 2026-10-09. Windows 11, Oracle JDK 25. Candidate JAR: 91,838 bytes; SHA-256 `437e61f9f98cf9d25d44ea5d7b65767c1a28f0da0d1ed094e68cc259654e9358`.

Maven verification passed 100 unit tests with no failures or skips. New tests cover SPSC publication across real threads, bounded wraparound and overflow, whole-prefix discard across capped drains, known ordered barrier replies, stale/foreign/reordered replies, teleport IDs, persistent input and missing movement packets, timer burst bookkeeping, non-finite samples and capped history. The physics kernel has 17 cases, including a float-bit regression for the client's reciprocal-multiply input normalization.

The synthetic packet workload constructs 600 independent inboxes and histories, then processes 2,160,000 primitive events. This local run took 532 ms and reported 200 thread-allocated bytes during the loop after construction. It excludes native decoding, Netty callbacks, sockets, world queries, region schedulers and real players. It is not a 600-player capacity or plugin-wide allocation result. The existing 720,000-sample pure movement workload also passed.

Paper 26.2-132 passed all 74 runtime assertions with the candidate JAR and stopped cleanly. In addition to the previous suite, the runner observes actual channel attachment, Ping/Pong, TickEnd, teleport confirmations and self velocity. A loopback TCP relay delays traffic in both directions: 100 ms produced a 213 ms measured barrier RTT; 300 ms produced 620 ms. At 150 ms with ordered jitter, the recorded RTT was 381 ms. These values are observations from one run, not latency bounds.

The packet observer survived an injected 350 ms owner-thread stall. Configuration reload disabled and reattached it without disabling ordinary checks. Final plugin disable removed the owned handler, verified on the channel event loop. No internal-check, observer-bind or region-ownership errors appeared in the Paper run. The preliminary Paper run is not counted as validation of the final candidate.

The warm Paper microbenchmark measured environment median/p95/max of 6.8/56.9/237.6 us and Reach-event 6.1/52.1/164.6 us over 800 measurements per operation. This is not a controlled before/after comparison or a measurement of total packet-observer overhead.

Folia 26.2-7 passed the same 74 assertions with the identical candidate JAR and stopped cleanly. The actual delayed TCP RTTs were 221 ms at 100 ms per direction, 615 ms at 300 ms per direction and 389 ms at 150 ms with ordered jitter. Observer reload, self velocity and final channel-handler removal passed without internal or region-ownership errors. Its warm environment median/p95/max was 5.4/57.4/274.0 us; Reach-event was 4.5/45.5/205.7 us. These remain one-player microbenchmarks, not distributed load validation.

No packet-based punishment or complete client-world reconstruction is shipped. The standalone ordinary physics kernel does not clip collision shapes, perform step-up or select a latency-compensated candidate. Player testing cannot substitute for implementing those parts. Large-scale distributed load, real client movement under lag and interactions with packet-rewriting plugins remain unvalidated.

All runtime data are fresh synthetic fixtures; production was not read or changed. The release JAR excludes test probes, worlds, logs, test classes and Netty dependencies.

## 0.3.0

Date: 2026-10-09. Windows 11, Oracle JDK 25. Candidate JAR: 64,035 bytes; SHA-256 `9543ce71dd6ca8dd8f244eef090c99bea727db12c25ce419580164c9902ea50a`.

Maven verification passed 72 unit tests with no failures or skips. Added cases cover token refill/bursts, backward clocks, native mining early-stop allowance, tool upgrades, lag-compensated mining, geometry crossings/grazing/visible corners, action-policy limits, immutable target history and stale/cross-world/future snapshots. A concurrent test submits work from 600 tasks through eight threads and verifies exactly 1,000 accepted cell reservations in a 50 ms window; stale callers cannot reopen an older window. It tests the budget, not live Folia regions.

The full Paper 26.2-132 and Folia 26.2-7 runtime runs each passed 63 assertions and stopped cleanly. Both used the same candidate JAR and the isolated setup described below; no production files were involved. No NordGuard internal-check or region-ownership errors appeared.

The new action suite invokes synthetic owner-thread Bukkit events. It verifies legal/far attack and block distances, custom interaction attributes and weapon components, premature mining, instant overrides including repeated starts, unknown starts, rate budgets, OBSERVE/OFF modes, bypass and preservation of prior cancellations. A separate Mineflayer case performs a real ordinary attack and mines stone with Reach, WallHit, BlockReach and FastBreak in CORRECT mode. Those actions must succeed. The synthetic gates do not prove that every forged client packet reaches the event or that every cheat variant is blocked.

NoClip has a test-only history injection across a stone wall, requiring its own evidence and return. This validates the sampled-path/setback integration, not a reproduced client exploit. Scan tests cover oversized bounds, a small unloaded area without chunk loading, dynamic trapdoors and a monotonic WallHit settling deadline. The movement fixture now keeps micro-hop and ordinary sprint-jump scenarios on its prepared floor; an earlier run that left the floor is not counted as a successful regression.

A warm one-player microbenchmark uses 100 scheduler ticks of warm-up and 800 recorded environment probes plus 800 Reach attack events, four calls per tick. These are local operation timings in synthetic terrain, not plugin-wide latency, a WallHit stress test or a 600-player capacity result. The 600-model workload below still excludes world and action checks.

| Platform / operation | Median us | p95 us | Maximum us |
| --- | ---: | ---: | ---: |
| Paper environment | 4.9 | 51.0 | 238.6 |
| Paper Reach event | 4.0 | 37.8 | 299.4 |
| Folia environment | 5.5 | 62.9 | 379.2 |
| Folia Reach event | 4.6 | 48.6 | 511.5 |

The released JAR excludes GuardProbe, unit tests, benchmark code, worlds and logs. Defaults remain OBSERVE; there are no automatic kicks or bans.

Remaining validation for 0.3.0: live cross-region combat with multiple real players, saturated spatial-budget fairness, real terrain/weapon/component variety, translated clients and packet timing, large-scale sustained load, and cheat-client reproductions. Two immutable history samples are not a complete lag-compensated packet timeline. These limits are part of the release scope, not hidden passing tests.

## Historical 0.2.0 validation

Date: 2026-10-09. Release candidate: 0.2.0. Windows 11, Oracle JDK 25.

### Unit tests

`mvn -B -ntp clean verify`: 43 tests passed, no failures or skipped tests. GitHub Actions also passed the source build and all 43 tests.

Coverage includes ordinary sprint jumps, sustained hover, wall ascent, sustained speed, isolated movement bursts, repeated excessive ascent, observed landing distance, exemptions, teleport resets, speed attributes, disabled checks, fall-damage accounting, invalid policy limits and time-conversion overflow. Post-reset anchor eligibility tests ensure an unchecked first position cannot count as clean movement. The native teleport sequence increment and wrap are tested separately.

New cases cover micro-hop speed below the previous allowance, repeated ordinary sprint-jump momentum, early Spider evidence, ordinary jumps against walls, artificial slow falling, source-surface oscillations, brief surface crossings, ordinary/fast ladder ascent, ordinary/excessive cobweb movement, medium transitions, supported ladder-bottom anchors, zero-gravity attributes, item-use slowdown and custom full-speed item-use components. A migration test preserves existing modes while missing new check entries default to OBSERVE.

### Reproduced 0.1.0 regression

The retained original release JAR was tested with immediate repeated flight after its first successful setback. The first correction passed; the second flight attempt failed to receive another correction within five seconds. This is a failing regression, not a passing cheat-coverage test. Version 0.1.1 changes anchor retention and the settling period to address it.

The workload case runs 600 independent movement models through 1,200 frames each: 720,000 samples. It excludes block queries, native state reads, entity schedulers, packets, other plugins and real players. It is not evidence of capacity at 600 online players.

### Isolated runtime checks

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

### Remaining validation

- Sustained high-load tests with world queries and distributed Folia regions.
- Normal play across real terrain, knockback, enchantments, movement attributes and interactions with production plugins.
- Actual cheat clients, translated clients and subtle packet-timing patterns.
- Damage cooldowns and partial early damage during a real fall; the native bridge deliberately respects normal damage immunity.

All checks ship in OBSERVE mode. Correction is experimental; there are no automatic kicks or bans. Passing these tests does not establish complete cheat coverage or absence of false positives.
