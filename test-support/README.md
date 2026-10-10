# Isolated runtime tests

For independent native movement and installed Wurst reproductions, see [native-client/README.md](native-client/README.md). Those tests use the actual Minecraft client rather than the constructed movement cases below.

Build the release with JDK 25 and Maven, then run `build-probe.ps1` with `-JavaHome` if needed. The probe script uses the current Windows user's default Maven cache; adjust dependency paths for a custom Maven repository.

```text
node test-support/integration.cjs <fresh-nordguard-test-directory> <java-executable> <test-runtime-seed> <Paper-or-Folia> <mineflayer-node_modules-directory>
```

Requires Mineflayer with Minecraft 26.2 support. Creates a fresh synthetic world at `127.0.0.1:25659` with one offline fixture account. A test-only TCP relay listens on `127.0.0.1:25660`. Both ports must be free; run platforms sequentially.

Only executable runtime files, libraries and an existing accepted EULA are copied from the seed. No worlds, player data, existing plugins or configuration are copied. The runner installs NordGuard and test-only GuardProbe, checks commands, geometry, scheduling, native damage, cancellation and a forged-ground movement scenario, then stops the server and saves local results.

The companion edits synthetic terrain, teleports its fixture, invokes native fall damage and temporarily changes check modes in memory. A separate fault-injection scenario clears server fall distance through the companion to test missing-damage recovery. That scenario is not proof of a working Wurst exploit. The runner also repeats flight immediately after setbacks, checks an external teleport starts a new return origin, verifies ordinary walking and keeps attempting excessive speed until three NordGuard corrections occur. Never deploy the companion publicly. Runtime directories, logs, private data and generated JARs do not belong in Git.

Version 0.2.0 adds moderate micro-hop packets, actual client-physics sprint jumps, a solid wall, source-water pool, ladder column and cobweb corridor. Each medium has an ordinary-movement case and a constructed excessive-movement case. Correction cases check both their own violation counter and NordGuard's correction counter: a vanilla server teleport does not count as a NordGuard pass. Spider also has a maximum attempted-height assertion.

Shield cases start item use through the server API and verify it remains active. The ordinary slow movement case must not flag; neither may a custom USE_EFFECTS component granting full movement speed. A separate ordinary shield with excessive movement must trigger NOSLOW and a NordGuard correction. This tests server-observed item state, not every Wurst use/release packet pattern.

These checks do not establish complete cheat coverage, absence of false positives, or capacity at 600 real players.

Version 0.3.0 adds synthetic attack, break and placement events on the fixture's owner thread. They test distance gates, observed mining starts, instant-break overrides (including repeated starts), action budgets, observation/off modes, explicit bypass and existing cancellations. These event tests are not cheat packet replays. The separate real Mineflayer attack/mining case checks that an ordinary attack deals damage and normal stone mining completes with FastBreak correction enabled.

The NoClip case injects a previous sampled position across a synthetic stone wall and requires both a report and return. It tests the check/setback integration, not a working client collision exploit. Geometry tests also cover oversized scans, a small unloaded area with no chunk loading, dynamic trapdoors, and the WallHit settling period. Delays use monotonic deadlines: catch-up scheduler ticks are not assumed to last 50 ms each.

A bounded microbenchmark warms up for 100 entity ticks, then records 200 ticks with four environment probes and four Reach attack events each. It prints the median, p95 and maximum of 800 measurements per operation. World geometry is synthetic and mostly stationary; this is not a distributed 600-player test or a full WallHit/NoClip cost measurement. The production JAR contains no benchmark code.

For development only, `NORD_GUARD_ACTIONS_ONLY=1` skips the older movement scenarios. Do not count that run as the full regression suite. Runtime results are written to the isolated directory, never the repository.

Version 0.4.0 checks native channel attachment, actual Ping/Pong barriers, TickEnd, matching teleport confirmations and outbound self velocity. The TCP relay delays real traffic by 100 or 300 ms in each direction, then adds ordered jitter at 150 ms. Barrier RTT must include the injected delay. These cases validate transport bookkeeping, not movement prediction under lag or complete latency compensation.

The companion deliberately stalls its fixture's owner thread for 350 ms, checks that observation survives, disables/re-enables packet collection via configuration reload and finally disables NordGuard. A channel-event-loop check requires the owned observer to be absent afterward. Do not run these fault-injection commands on production. There is still no 600-client/network/region capacity test.

Version 0.5.0-rc.2 enables the optional world cache temporarily and resends one synthetic chunk through the real channel. The fixture checks decoded block IDs against native states and checks the acknowledged geometry lookup at the player's feet and body. Native adapter tests also validate dimension metadata from an actual respawn packet.

The runner then withholds real client Pong replies for 1.1 seconds and sends a packet-only stone block above the fixture. It requires the confirmed cache to retain the old block while the geometry lookup returns unknown for that dirty chunk. Releasing replies in order must commit the edit. A second packet restores the original state and must also become confirmed. This does not modify the server world or prove that a hostile client obeys packets. Unit tests cover prefix boundaries, loss, malformed data, timeouts, replayed IDs, dimension changes and capacity limits separately.

Version 0.5.0-rc.3 enables packet prediction temporarily after preparing a flat fixture in the middle of one owned loaded chunk. It suppresses the bot's automatic movement packets during this case, sends explicit Move and TickEnd pairs through the actual socket and requires the live predictor to seed and accept stationary frames. Twenty constructed ordinary walking frames must increase accepted predictions without mismatches. Four excessive displacements must produce mismatches in observation only. The runner then restores ordinary configuration and continues the full existing suite. This is constructed traffic, not an independent official-client replay or a Wurst reproduction.

Version 0.5.0-rc.4 injects an already-expired setback ticket into the fixture's session and invokes its real owner tick. The test requires cleared busy/anchor state and a newer origin revision, rejects the old completion and verifies that an old ticket cannot consume a newer one. These three assertions test bookkeeping, not cancellation of an actual in-flight teleport. Existing repeated speed/flight and medium correction cases still run. Test-only return-state diagnostics help distinguish missing anchors from pending returns; they are not included in the production plugin.
