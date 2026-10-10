# Native Minecraft client tests

Windows-only isolated integration fixture for the installed Minecraft 26.2 client, Fabric Loader 0.19.5, Fabric API 0.161.0 and Wurst 7.56. Requires JDK 25 and a working graphics driver. These tests launch a real game process; they are not a headless capacity benchmark.

Build NordGuard and `test-support/build-probe.ps1` first. Then:

```text
node test-support/native-client/build.cjs <Prism-data-directory> <JDK-directory>
node test-support/native-client/integration.cjs <fresh-nordguard-test-directory> <JDK-directory> <test-runtime-seed> <Paper-or-Folia>
```

The builder reads installed version metadata and libraries, plus Fabric API and Wurst from `instances/26.2/minecraft/mods`. It does not read Prism accounts, access tokens or personal game settings. Generated fixtures and launch metadata stay in the ignored `test-support/build/native-client` directory. No Minecraft, Fabric or Wurst binaries are committed or included in the server release. Separate test data are not an operating-system sandbox: installed third-party mods still run with the desktop user's permissions.

The runner copies only server executables, libraries and a previously accepted test EULA from the seed. It creates a fresh flat world, loopback-only server and separate game directory. Its offline account is `GuardFixture`; the launch token is the dummy value `0`, not a personal credential. Expected authentication/Realms failures from that dummy token do not establish a server or anticheat failure. Never use this offline test configuration on a public listener.

Ports 25659 and 25660 must be free. Run Paper and Folia sequentially. The client fixture refuses any connected server other than `127.0.0.1:25660`, drives native movement keys and switches only four installed Wurst modules. It does not forge ordinary movement coordinates. After the clean Fabric-client cases, a separate client launch adds Wurst; each hostile case verifies the requested module actually enabled. The runner stops only its own child processes.

The matrix covers walking, sprinting, sprint-jumping and sneaking, then Flight, SpeedHack, Spider and Jesus. The TCP relay applies 0, 100 and 300 ms in each direction; the 300 ms case adds ordered jitter. Both relay sockets explicitly disable Nagle buffering, so an unlabelled transport setting does not add another batching profile. Set `NORD_NATIVE_NAGLE=1` to test buffering separately; the JSON records `noDelay`. See the [Node socket documentation](https://nodejs.org/api/net.html#socketsetnodelaynodelay). This switch affects only the test relay, not NordGuard or a production connection.

Every ordinary case requires actual movement and no new movement violations or corrections. Each Wurst case requires both its own check counter and a completed NordGuard correction; a vanilla teleport cannot pass it. Failures remain in the report while later cases continue. Module configuration is Wurst's default configuration in the fresh fixture, not an exhaustive settings search. `NORD_NATIVE_ORDINARY_ONLY=1` skips Wurst for development; such a run is never a full matrix pass and its scope is recorded in JSON.

Set `NORD_NATIVE_DURATION_MS` to an integer from 2000 to 30000 for longer ordinary input cases; the default is 2000. The result records that duration. Longer cases turn through native yaw input between one and twelve blocks from their origin, staying inside the prepared strip. They check repeated jump cycles rather than allowing one short burst to conceal accumulating debt. A dead client invalidates the run; it is not a successful stationary movement case.

Prediction diagnostics are recorded separately. Zero mismatches with zero accepted predictions is not a predictor pass. Preparing synthetic terrain can invalidate the optional bounded block cache; unknown geometry must defer, not become air. This runner does not force chunk resends to hide such deferrals. A test-only 100-tick trace records owner positions, jump momentum, vertical displacement and speed debt during sprint-jump cases and the 100 ms sprint case. Helper access errors invalidate the run. None of the file polling, trace logging or module reflection runs in the server release.

Set `NORD_NATIVE_EXTENDED=1` to add half-block transitions, ice, honey, slime, soul sand, Speed and Jump Boost expiry, a server-issued impulse, cobwebs, a short ladder ascent and a 120-second sprint-jump patrol at 100 ms per direction with ordered jitter. Each scenario must produce movement and leave all ten movement counters and completed-correction counts unchanged. Ladder input stops after 2.5 seconds so the player cannot run off the top of the fixture. The helper clears potion effects, velocity, hunger and saturation between cases. These are synthetic terrain and server-state changes with native client input, not tests of third-party plugin interactions or elytra transitions.

Set `NORD_NATIVE_PROFILE=1` together with the extended matrix to record the isolated server during the two-minute patrol using the installed JDK's JFR profile settings. The runner attaches only to its own child server PID and writes `native-soak.jfr` outside Git. A recording from one player is not a distributed load test or a production overhead estimate. The optional predictor and world replica remain enabled in this fixture, unlike release defaults.

The runner also requires positive predictor seeds, accepted frames and candidate trials during the initial zero-added-delay walking case. It does not force chunk resends. Later unknown geometry and unsupported contexts still defer; positive initial coverage does not validate the full predictor.

Local outputs include `native-results.json`, server/client logs and prediction diagnostics. Keep raw logs, worlds, player data and generated JARs out of Git. Publish an aggregate result in `TESTING.md`, including failing cases. These single-client tests do not validate 600-player capacity, every Wurst feature, cross-region combat, all special movement physics or a stable correction mode.
