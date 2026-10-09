# Isolated runtime tests

Build the release with JDK 25 and Maven, then run `build-probe.ps1` with `-JavaHome` if needed. The probe script uses the current Windows user's default Maven cache; adjust dependency paths for a custom Maven repository.

```text
node test-support/integration.cjs <fresh-nordguard-test-directory> <java-executable> <test-runtime-seed> <Paper-or-Folia> <mineflayer-node_modules-directory>
```

Requires Mineflayer with Minecraft 26.2 support. Creates a fresh synthetic world at `127.0.0.1:25659` with one offline fixture account. The port must be free; run platforms sequentially.

Only executable runtime files, libraries and an existing accepted EULA are copied from the seed. No worlds, player data, existing plugins or configuration are copied. The runner installs NordGuard and test-only GuardProbe, checks commands, geometry, scheduling, native damage, cancellation and a forged-ground movement scenario, then stops the server and saves local results.

The companion edits synthetic terrain, teleports its fixture, invokes native fall damage and temporarily sets NoFall to CORRECT in memory. A separate fault-injection scenario clears server fall distance through the companion to test missing-damage recovery. That scenario is not proof of a working Wurst exploit. Never deploy the companion publicly. Runtime directories, logs, private data and generated JARs do not belong in Git.

These checks do not establish complete cheat coverage, absence of false positives, or capacity at 600 real players.
