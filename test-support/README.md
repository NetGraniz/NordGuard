# Isolated runtime tests

Build the release with JDK 25 and Maven, then run `build-probe.ps1` with `-JavaHome` if needed. The probe script uses the current Windows user's default Maven cache; adjust dependency paths for a custom Maven repository.

```text
node test-support/integration.cjs <fresh-nordguard-test-directory> <java-executable> <test-runtime-seed> <Paper-or-Folia> <mineflayer-node_modules-directory>
```

Requires Mineflayer with Minecraft 26.2 support. Creates a fresh synthetic world at `127.0.0.1:25659` with one offline fixture account. The port must be free; run platforms sequentially.

Only executable runtime files, libraries and an existing accepted EULA are copied from the seed. No worlds, player data, existing plugins or configuration are copied. The runner installs NordGuard and test-only GuardProbe, checks commands, geometry, scheduling, native damage, cancellation and a forged-ground movement scenario, then stops the server and saves local results.

The companion edits synthetic terrain, teleports its fixture, invokes native fall damage and temporarily changes check modes in memory. A separate fault-injection scenario clears server fall distance through the companion to test missing-damage recovery. That scenario is not proof of a working Wurst exploit. The runner also repeats flight immediately after setbacks, checks an external teleport starts a new return origin, verifies ordinary walking and keeps attempting excessive speed until three NordGuard corrections occur. Never deploy the companion publicly. Runtime directories, logs, private data and generated JARs do not belong in Git.

Version 0.2.0 adds moderate micro-hop packets, actual client-physics sprint jumps, a solid wall, source-water pool, ladder column and cobweb corridor. Each medium has an ordinary-movement case and a constructed excessive-movement case. Correction cases check both their own violation counter and NordGuard's correction counter: a vanilla server teleport does not count as a NordGuard pass. Spider also has a maximum attempted-height assertion.

Shield cases start item use through the server API and verify it remains active. The ordinary slow movement case must not flag; neither may a custom USE_EFFECTS component granting full movement speed. A separate ordinary shield with excessive movement must trigger NOSLOW and a NordGuard correction. This tests server-observed item state, not every Wurst use/release packet pattern.

These checks do not establish complete cheat coverage, absence of false positives, or capacity at 600 real players.
