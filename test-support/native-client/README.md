# Native Minecraft client tests

Windows-only isolated integration fixture for the installed Minecraft 26.2 client, Fabric Loader 0.19.5, Fabric API 0.161.0 and locally supplied Wurst. Requires JDK 25 and a working graphics driver. These tests launch a real game process; they are not a headless capacity benchmark. `NORD_NATIVE_WURST_PATH` selects a specific local archive; result JSON records its version and SHA-256. The 0.5.3 regressions use the supplied 7.56.1 archive.

Build NordGuard and `test-support/build-probe.ps1` first. Then:

```text
node test-support/native-client/build.cjs <Prism-data-directory> <JDK-directory>
node test-support/native-client/integration.cjs <fresh-nordguard-test-directory> <JDK-directory> <test-runtime-seed> <Paper-or-Folia>
```

The builder reads installed version metadata and libraries, plus Fabric API and Wurst from `instances/26.2/minecraft/mods`. It does not read Prism accounts, access tokens or personal game settings. Generated fixtures and launch metadata stay in the ignored `test-support/build/native-client` directory. No Minecraft, Fabric or Wurst binaries are committed or included in the server release. Separate test data are not an operating-system sandbox: installed third-party mods still run with the desktop user's permissions.

The runner copies only server executables, libraries and a previously accepted test EULA from the seed. It creates a fresh flat world, loopback-only server and separate game directory. Its offline account is `GuardFixture`; the launch token is the dummy value `0`, not a personal credential. Expected authentication/Realms failures from that dummy token do not establish a server or anticheat failure. Never use this offline test configuration on a public listener.

Ports 25659 and 25660 must be free. Run Paper and Folia sequentially. The client fixture refuses any connected server other than `127.0.0.1:25660`. It supports the original four movement modules plus Criticals, KillAura, BoatFly and FastBreak. It does not forge ordinary movement coordinates. The default movement matrix launches a clean Fabric client before adding Wurst; regression selectors have their own recorded scope. Each hostile case verifies the requested module actually enabled. The runner stops only its own child processes.

The default matrix covers walking, sprinting, sprint-jumping and sneaking, then Flight, SpeedHack, Spider and Jesus. The TCP relay applies 0, 100 and 300 ms in each direction; the 300 ms case adds ordered jitter. Each direction has an explicit FIFO queue, one head timer and backpressure handling. It uses a monotonic clock and caps queued payload at 8 MiB / 8,192 entries. Run `node --test test-support/delay-proxy.test.cjs` before trusting latency results; the unit test now uses ephemeral ports. It verifies all 6,000 bytes in both directions. The former independent per-chunk timers failed this test and could reorder bytes; older jitter runs are not valid FIFO-network evidence. Node does not guarantee callback ordering for [setTimeout](https://nodejs.org/api/timers.html#settimeoutcallback-delay-args).

### Combat, vehicle and mining regressions

`NORD_NATIVE_COMBAT_ONLY=1`, `NORD_NATIVE_BOATS_ONLY=1` or `NORD_NATIVE_MINING_ONLY=1` selects one suite. `NORD_NATIVE_REGRESSION_ONLY=1` combines them. `NORD_NATIVE_COMBAT_DELAYS` and `NORD_NATIVE_MINING_DELAYS` accept subsets of `0,100,300`. `NORD_NATIVE_BOAT_HOSTILE_ONLY=1` omits the unexercised native bubble/impulse launch controls; that scope is explicit in JSON. Do not present a selected run as the default full movement matrix.

Combat uses a synthetic high-health target and actual health loss, cancellation and critical flags. Boat tests require actual ascent before testing a correction and verify the same passenger tree afterward. Mining uses ordinary native attack input, fixed server-side tools and actual blocks/drops. The test-only client closes its own PauseScreen and models the grabbed-input bit for background mining without requesting OS focus or cursor capture; it restores that bit on idle/error. It fixes the selected test slot and drains drop clicks so unrelated desktop input does not invalidate the weapon fixture. No such controls are included in NordGuard's server JAR.

Both relay sockets explicitly disable Nagle buffering, so an unlabelled transport setting does not add another batching profile. Set `NORD_NATIVE_NAGLE=1` to test buffering separately; the JSON records `noDelay`. See the [Node socket documentation](https://nodejs.org/api/net.html#socketsetnodelaynodelay). This switch affects only the test relay, not NordGuard or a production connection.

Every ordinary case requires actual movement and no new movement violations or corrections. Each Wurst case requires both its own check counter and a completed NordGuard correction; a vanilla teleport cannot pass it. Failures remain in the report while later cases continue. Module configuration is Wurst's default configuration in the fresh fixture, not an exhaustive settings search. `NORD_NATIVE_ORDINARY_ONLY=1` skips Wurst for development; such a run is never a full matrix pass and its scope is recorded in JSON.

Set `NORD_NATIVE_DURATION_MS` to an integer from 2000 to 30000 for longer ordinary input cases; the default is 2000. The result records that duration. Longer cases turn through native yaw input between one and twelve blocks from their origin, staying inside the prepared strip. They check repeated jump cycles rather than allowing one short burst to conceal accumulating debt. A dead client invalidates the run; it is not a successful stationary movement case.

Prediction diagnostics are recorded separately. Zero mismatches with zero accepted predictions is not a predictor pass. Preparing synthetic terrain can invalidate the optional bounded block cache; unknown geometry must defer, not become air. This runner does not force chunk resends to hide such deferrals. A test-only 300-tick trace records owner positions, jump momentum, vertical displacement and speed debt during sprint-jump cases and the 100 ms sprint case. Helper access errors invalidate the run. None of the file polling, trace logging or module reflection runs in the server release.

The downstep cases add twelve seconds of clean sprint-jump warmup followed by twelve seconds across a one-block drop. Minimum native height must confirm a descent of at least 0.9 blocks. `NORD_NATIVE_DOWNSTEP_ONLY=1` skips the ordinary walk/sprint/jump/sneak and predictor-coverage matrix, but retains downstep, Wurst and lifecycle cases. `NORD_NATIVE_DOWNSTEP_DELAYS` selects a comma-separated subset of `0,100,300`; both controls are recorded in JSON. Such a focused run is not a full ordinary/predictor matrix pass.

`NORD_NATIVE_WURST_PATH` can select another local Wurst archive for the isolated client. Results record its filename-derived version and SHA-256. The file is not installed in the user's Prism instance, committed or packaged with NordGuard. Only the same four movement modules are driven; supplying another archive does not add BoatFlight or FastBreak coverage.

Set `NORD_NATIVE_EXTENDED=1` to add half-block transitions, ice, honey, slime, soul sand, Speed and Jump Boost expiry, a server-issued impulse, cobwebs, a short ladder ascent and a 120-second sprint-jump patrol at 100 ms per direction with ordered jitter. Each scenario must produce movement and leave all ten movement counters and completed-correction counts unchanged. Ladder input stops after 2.5 seconds so the player cannot run off the top of the fixture. The helper clears potion effects, velocity, hunger and saturation between cases. These are synthetic terrain and server-state changes with native client input, not tests of third-party plugin interactions or elytra transitions.

Set `NORD_NATIVE_PROFILE=1` together with the extended matrix to record the isolated server during the two-minute patrol using the installed JDK's JFR profile settings. The runner attaches only to its own child server PID and writes `native-soak.jfr` outside Git. Run `node test-support/jfr-summary.cjs <JDK-directory> <isolated-native-soak.jfr>` to aggregate CPU samples, execution stacks, GC pauses and heap snapshots at collections. Stack samples and GC heap snapshots are not a complete CPU attribution or a peak-heap measurement. A recording from one player is not a distributed load test or a production overhead estimate. The optional predictor and world replica remain enabled in this fixture, unlike release defaults.

The runner also requires positive predictor seeds, accepted frames and candidate trials during the initial zero-added-delay walking case. It does not force chunk resends. Later unknown geometry and unsupported contexts still defer; positive initial coverage does not validate the full predictor.

After the movement matrix, the helper disables and enables the same plugin instance twice while the client remains connected. Each cycle requires channel cleanup, an attached session, fresh native acknowledgements and progressing owner samples. This tests lifecycle recovery; it does not recommend production hot-reloading.

Local outputs include `native-results.json`, server/client logs and prediction diagnostics. Keep raw logs, worlds, player data and generated JARs out of Git. Publish an aggregate result in `TESTING.md`, including failing cases. These single-client tests do not validate 600-player capacity, every Wurst feature, cross-region combat, all special movement physics or a stable correction mode.

`NORD_NATIVE_DOWNSTEP_ONLY=1` selects the warmed-up jump route. Set
`NORD_NATIVE_DESCENT_SCENARIOS=wall-run,mountain-wall,downstep` to include wall-adjacent
running and a descending staircase. Routes patrol inside prepared terrain;
wall cases require actual wall proximity in owner traces. Use
`NORD_NATIVE_DOWNSTEP_DELAYS=0,100,300` for added per-direction delays.

`diagonal-1`, `diagonal-2` and `diagonal-mixed` select a descending stone
corridor at 45 degrees to the block grid. Prefix the profile with
`diagonal-15-` instead (`diagonal-15-1`, `diagonal-15-2`,
`diagonal-15-mixed`) for 15 degrees. Each route has twelve seconds of
flat sprint-jump warmup, then seven seconds (six at 15 degrees to stay inside
the finite terrain) of continuous native forward,
sprint and jump input without patrol turns. Floors drop every three X blocks
by one block, two blocks, or alternating one/two blocks. The heading angle is
not the slope's inclination. Actual X/Z travel and descent must occur;
the result records input yaw, minimum height, violations and completed
corrections. Test-only owner traces also record Flight score, support height,
jump age and air age. The ordinary client does not load Wurst.

Set `NORD_NATIVE_DESCENT_REGRESSION=1` to append Wurst movement and surface
controls to the selected route matrix. `NORD_NATIVE_HOSTILE_DELAYS` selects the
movement-control delays (0/100/300 ms); result metadata records these selections.

`NORD_NATIVE_SURFACE_ONLY=1` uses the supplied Wurst Jesus and SnowShoe modules on
synthetic source-water/snow surfaces without a land anchor. Each module needs a
detect-only positive control and a completed CORRECT recovery. Point
`NORD_NATIVE_WURST_PATH` at the explicitly selected client archive; its digest is
recorded in the result. These fixtures connect only to the loopback test server.
