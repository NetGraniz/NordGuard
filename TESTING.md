# Test record

## 0.5.0-rc.3 — stage 2

Date: 2026-10-10. Windows 11, Oracle JDK 25. Candidate JAR: 162,524 bytes; SHA-256 `c9abae01427da67bf58311489a9a116e8faabd41a2f281bae5d2bc4a4d563109`. Production was not accessed or modified. This is an observation-only integration, not a stable complete anticheat.

Local Maven verification passed 202 unit tests with no failures or skips. The 25 new cases exercise actual frame assembly, five-frame supported-rest seeding, 200 walking frames, ordinary jump/landing, a fixed sprint context, omitted moves, unknown geometry, forged ground claims without support, owner disagreement, duplicate movement, geometry revision changes between Move and TickEnd, transitions, frame/cell budget exhaustion, two-frame drain caps, monotonic tick credit across resets, long gaps/non-finite input, excess movement and unsupported owner attributes. Scene tests cover support/friction agreement across both branches, denied/oversized scans before any reads, unknown halo cells and missing alternate support. Settings tests cover opt-in defaults, required dependencies and bounded typed values.

The integrated model runs from real packet callbacks drained on the entity owner. It collects acknowledged static geometry for both candidate origins and keeps computed momentum; it never sets velocity from observed displacement. Move frames finish at TickEnd, with independent monotonic tick credit and shared work budgets. Geometry or context uncertainty defers. Mismatches affect only administrator-requested diagnostics, not existing evidence counters, alerts or corrections.

Native verification used the installed official 26.2 client bytecode: `Minecraft.tick` sends `ServerboundClientTickEndPacket.INSTANCE`, and `LocalPlayer.tick` calls `sendPosition`. Runtime fixtures construct packets explicitly rather than replay the official client. Pure trajectory tests also use the arithmetic kernel, so neither establishes complete independent client parity.

The first Paper run passed the full suite using an earlier JAR. Final review then excluded custom air-drag/friction modifiers and nondefault walk-speed settings; the earlier run is not validation of the published binary. The final candidate keeps both prediction and its block cache off by default. At most two frames run per owner drain, each with 512 scene cells, 256 shapes and 36 candidate trials; default global budgets are 2,000 frames/s and 250,000 cells/s. Budget exhaustion deliberately loses coverage. These are work-count limits, not timing guarantees or proof of 600-player capacity.

GitHub Actions run `38021588481` passed all 202 tests and a clean build at source commit `09e7292d02e028210a457483323bf5d8263a8fc3`.

Paper 26.2-132 passed all 87 full runtime assertions with the final candidate and stopped cleanly. The actual socket case produced 12 TickEnd frames with one rest seed and seven accepted predictions, followed by 20 ordinary walking frames: 27 accepted, zero mismatches and 486 trials in total. Four excessive displacements then produced four mismatches, no new seed and 558 trials. These are constructed packet scenarios, not official-client replay or Wurst coverage. Previous world-journal, native geometry, movement/action, ordinary attack/mining, delayed TCP, stall, reload and observer-cleanup checks also passed without NordGuard internal or region-ownership errors. Directory: `nordguard-test-20261010-paper-050rc3-r2`.

Paper RTTs were 206/626/388 ms at 100/300/150 ms per-direction delay (last with ordered jitter). The warm existing environment median/p95/max was 4.0/42.1/257.2 us; Reach-event was 3.3/34.3/331.3 us over 800 measurements each. Prediction was disabled during this microbenchmark; these numbers do not measure the new path's overhead or prove a performance improvement. The Spider fixture's observed attempted height was 1.20 blocks before correction; it is not a universal bound.

The first Folia attempt stopped at the existing withheld-Pong fixture before predictor testing: a random spawn near a chunk edge made its `x + 2` test block fall into an adjacent chunk that the fixture had not resent. Both raw and safe lookups were unknown, so the expected old known value could not be asserted. The test companion now puts the temporary block in the center of the player's captured chunk. This changes test setup only; the production JAR is unchanged. The failed run is not counted as a passing platform test.

Folia 26.2-7 passed all 87 full runtime assertions with the identical final JAR and stopped cleanly. Its real socket prediction case matched Paper's counters: one seed, seven accepted rest frames, 27 accepted frames after walking with zero mismatches, then four mismatches from excessive movement and 558 total trials. World-journal, native geometry, existing checks, ordinary actions, TCP delay, owner stall, reload and cleanup also passed without NordGuard internal or region-ownership errors. Directory: `nordguard-test-20261010-folia-050rc3-r2`.

Folia RTTs were 220/616/561 ms at the same delay settings. Existing environment median/p95/max was 5.3/62.9/645.2 us; Reach-event was 5.2/56.4/561.4 us. Prediction was disabled during this microbenchmark, with the same limitations as Paper. The Spider fixture reached an observed 1.20 blocks before correction. Build, Paper and Folia JAR hashes matched. The JAR contains no test companion, logs, worlds or bundled Netty. No test Java process remained afterward.

Current limits: server attributes and sprint state are owner snapshots, not acknowledged historical attributes. Rest seeding is a conservative hypothesis, not proof of the client's hidden velocity. Entity collisions, world borders, special movement, sprint transitions and packet rewriting remain outside the validated contract and may produce diagnostic mismatches. Some transitions invalidate the block cache until chunks are naturally resent. Clients omitting TickEnd get no prediction; original checks remain active. Full native step-up parity, real client replays, predictor corrections and distributed performance/allocation testing remain gates for stages 3–5.

## 0.5.0-rc.2 — stage 1

Date: 2026-10-09. Windows 11, Oracle JDK 25. Candidate JAR: 146,820 bytes; SHA-256 `626ef3e0da7c0b54b5faaa2b30ea4b89860716c4c521a0e360a26af8349f2a6b`. No production files were accessed or changed. The predictor remains disconnected from runtime checks.

Local verification passed 177 unit tests with no failures or skips. The 22 new journal cases cover matching/foreign/duplicate/reused barrier IDs, exact prefix commits, newer pending changes, unaffected chunks, reordered/expired/backward replies, timeout cleanup, entry/barrier/aggregate-byte caps, repeated ring reuse, malformed or budget-denied decoding, dimension reset, retained-area pruning, invalid block batches, denied copy-on-write materialization, movement-event ordering, inbox overflow and observer/context loss.

GitHub Actions run `37989485147` passed all 177 tests and a clean build at source commit `356164eb876b94c0161058f38d20a392714168b9`.

Paper 26.2-132 passed all 83 full runtime assertions with this exact JAR and stopped cleanly. New cases withheld actual Pong replies, checked that a pending packet-only edit left the old confirmed block unchanged and the dirty geometry unknown, then released ordered replies and verified both the edit and restoration. Native respawn metadata, native decoding, default-off and opt-in lifecycle, previous movement/action checks, ordinary attack/mining, delayed TCP, an owner-thread stall and handler cleanup passed without NordGuard internal or region-ownership errors. The isolated directory is `nordguard-test-20261009-paper-050rc2-r1`.

Paper measured barrier RTTs of 219/625/429 ms for 100/300/150 ms per-direction delay (last with ordered jitter). Warm one-player environment median/p95/max was 7.5/75.6/671.1 us; Reach-event was 6.5/75.0/353.9 us over 800 measurements per operation. This benchmark runs with the optional cache disabled and does not measure the new journal's end-to-end overhead or prove a performance improvement.

Folia 26.2-7 passed the same 83 full runtime assertions with the identical JAR and stopped cleanly. Actual withheld-Pong commits, native metadata, cache lifecycle, existing checks, ordinary actions, delayed TCP, stall survival and final channel cleanup passed without NordGuard internal or region-ownership errors. Its isolated directory is `nordguard-test-20261009-folia-050rc2-r1`. Recorded RTTs were 220/612/515 ms at the same delay settings. Environment median/p95/max was 6.8/60.6/393.5 us; Reach-event was 6.2/50.1/416.6 us. These are one-player operation measurements with the same limitations as Paper, not a plugin-wide load result. All three JAR hashes (build, Paper copy and Folia copy) matched.

Failed world-write handling is implemented through a channel promise listener; these runs do not inject an actual failed channel write. Unit tests exercise the resulting observer-loss invalidation. That distinction remains a validation limit.

World changes no longer update the confirmed cache immediately. A sent Ping snapshots the journal sequence; its ordered matching Pong commits only that prefix. The future predictor's geometry lookup returns unknown for dirty chunks. This does not authenticate the client, retain every possible historical branch, or integrate movement prediction. Unknown initial or previously untracked chunks can remain unknown until naturally resent. All block events invalidate knowledge rather than simulate moving blocks; this can reduce coverage in active builds.

The journal adds a separate 16-update / 512 KiB limit and four barriers per opted-in player. Together with the inbox and confirmed cache, accounted payload can reach 1.5 MiB per player before headers and temporary allocations. The optional cache stays off by default. Existing global work budgets and checks are unchanged. There is no distributed 600-player capacity result, controlled comparison with another anticheat, or claim that these tests establish a CPU-time ceiling.

Remaining stages: integrate ordinary packet prediction and scene/support selection; handle special movement and safe corrections; test normal and hostile real clients under lag; then validate sustained distributed load and tune defaults before a stable release. Entity collisions, complete native step-up parity and packet-rewriting plugin interactions still need validation. Stage 1 is a bounded acknowledged-prefix foundation, not the completed anticheat.

## 0.5.0-rc.1

Date: 2026-10-09. Windows 11, Oracle JDK 25. Candidate JAR: 140,703 bytes; SHA-256 `c95c45ca3fe577c3fe7310d53aa77d5b5fe7bbcef9bd10fb6dedbad61a0ee744`. This is a release candidate. The new predictor is not connected to punishment or to live packet processing.

Maven verification passed 155 tests with no failures or skips. Added coverage includes the exact 26.2 chunk-section format (two counters and fixed-length packed words), local/global block palettes, biome skipping, truncation and size limits, detached snapshots, unknown/forgotten chunks, copy-on-write updates, LRU bounds, materialization budget denial, tracked-area eviction and FIFO decoding of one chunk per drain. Four publication tests include 40,000 mixed events crossing real threads and reusing ring slots; they check payload identity, cleared references and exact accounting. These finite tests do not prove absence of every race.

The collision kernel has 18 unit cases for free motion, floors, walls, partial blocks, step-up, descent, head clearance, negative coordinates, axis order and unsupported bounds. The predictor has 16 tests: ordinary 300-tick trajectories, sprint jumps, walls, slabs, missing position reports, sustained excessive speed, accumulated small excess, and a two-state slab ambiguity regression. Tests generate legal trajectories from the same arithmetic kernel, so they are not independent proof of parity with the real client. Runtime native differential tests cover axis clipping separately; complete native step-up and client replay validation remain unfinished.

One recorded local synthetic run took 373 ms for 36,000 predictor frames (648,000 candidate trials) and 481 ms for 2,160,000 primitive packet events. They exclude real sockets, live world collection, region scheduling, client rendering and other plugins. These numbers do not establish capacity or allocation rates at 600 online players. The predictor is not invoked by the shipped runtime. The outbound cache is off by default and does not change the existing 17 checks.

Paper 26.2-132 passed all 80 runtime assertions with this exact candidate and stopped cleanly. The new native oracle compares 1,000 nonpenetrating axis-clipping scenes against the actual server implementation; this does not validate native step-up. Native outbound chunk decoding checks 64 positions in every section (1,536 samples over 24 sections). Other cases verify native single-block and section-block coordinates/state IDs, budget denial, not-ready chunks, far filtering before allocation, block-event invalidation, forget-to-unknown semantics and extracted air geometry. A fresh chunk sent through the actual channel is captured, decoded on the owner scheduler and compared at the player's feet and body. No production data are used.

Paper also passed default-off cache, opt-in reload and cache release, the existing movement/action suite, real ordinary client attack/mining, delayed TCP, an injected 350 ms owner-thread stall, observer reload and final handler removal. Recorded RTTs were 217/619/512 ms for 100/300/150 ms per-direction delay (the last includes ordered jitter). The warm one-player environment median/p95/max was 8.2/78.4/18142.9 us; Reach-event was 7.0/64.0/427.8 us. The 18.14 ms environment outlier was not isolated to a cause. These measurements do not establish an execution-time ceiling, absence of performance regressions or distributed capacity. No NordGuard internal-check, observer-bind or region-ownership errors appeared.

Preliminary Paper and Folia runs passed 80 assertions each, but used an earlier JAR. Final review then added observer cleanup when entity scheduling returns null; preliminary results are not counted as validation of the published binary. The source build at commit `6309d26` passed all 155 tests in GitHub Actions run `37985576468`.

Folia 26.2-7 passed the same 80 assertions with the identical final candidate JAR and stopped cleanly. It passed the native chunk/geometry checks, actual channel capture, default-off and opt-in cache lifecycle, existing movement and action checks, delayed TCP, owner-thread stall and channel cleanup without NordGuard internal-check, observer-bind or region-ownership errors. Recorded RTTs were 220/617/307 ms at 100/300/150 ms per direction (the last with ordered jitter). Its warm environment median/p95/max was 4.5/43.8/491.6 us; Reach-event was 3.5/34.1/298.2 us. This is one synthetic player, not a distributed-load or controlled performance comparison. Final directories are `nordguard-test-20261009-paper-050rc1-r2` and `nordguard-test-20261009-folia-050rc1-r2`; neither is a source checkout or a production server.

Remaining release gates: acknowledged historical client-world state rather than latest outbound data; complete ordinary collision-scene collection and support/friction selection; entity collisions and movement contexts; integrated prediction with safe reset/correction rules; independent native step-up/client replay tests; normal play under lag with real clients; and sustained distributed load with the intended plugin set. Player testing alone cannot substitute for the unimplemented integration. Do not treat this candidate as a stable full anticheat.

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
