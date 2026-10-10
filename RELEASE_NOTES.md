# NordGuard 0.5.0-rc.8 — preserve observed world across player state changes

Release candidate for Minecraft 26.2 on Paper and Folia, Java 25. This is a prerelease, not a complete stable anticheat or a validated 600-player deployment. All checks default to OBSERVE. Existing configurations keep their selected modes; updating the JAR does not turn an existing CORRECT setting back into OBSERVE.

## Changes

Self attributes, effects and entity metadata now reset physics confidence without discarding the observed chunk stream. Pending block changes remain unknown until the matching barrier; these player-state packets cannot acknowledge them. Dimension changes, uncertain world data, barrier ambiguity and observer loss remain conservative.

The test relay now uses an explicit FIFO queue and one monotonic head timer per direction. Its former independent timers failed a raw byte-order reproduction. Earlier latency results are qualified in the test record; no movement threshold or physics rule was relaxed for that test defect. CI verifies all 6,000 bytes in both directions.

The native fixture adds terrain, effects and expiry, server-issued knockback, cobwebs, ladder ascent and a two-minute patrol with an optional isolated-server JFR recording. It requires fresh client-state snapshots and nonzero initial native predictor coverage. Test helpers, clients, logs and recordings are not bundled in the plugin.

## Validation and limits

Local verification and GitHub Actions run 38080182618 passed 247 unit tests and the separate FIFO byte regression. The final corrected-fixture Paper matrix passed 12 ordinary six-second cases and 12 actual Wurst cases; Folia passed those cases plus 11 extended ordinary scenarios. No ordinary movement counter or correction increased. All 24 hostile cases required their own check evidence and a completed NordGuard correction. Results, exact runtime builds, hashes, profiling scope and failed intermediate runs are recorded in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md).

The same archive passed all 91 synthetic server-runtime assertions on each platform, including repeated returns, teleports, fall damage, action gates, delayed traffic, reload and observer cleanup. All four final installed copies matched the candidate hash, test servers stopped cleanly, and no test listeners remained.

Initial native predictor seeds, accepted frames and trials are now nonzero on both platforms. Those counters include stationary frames; later unknown geometry still defers. The predictor and replica remain opt-in, disabled by default and unable to punish players. Full acknowledged attribute history, special/entity-collision physics, broader elytra/transitions and production-plugin interactions remain unvalidated.

The main change adds no world scan, repeating task or dependency and retains existing cache/work bounds. A two-minute one-player JFR recording is not an A/B overhead comparison or distributed capacity proof. A separate sustained load test is still needed for the intended 600-player deployment. No automatic bans or kicks were added. Production configurations, worlds and account data were not accessed or changed.

Candidate JAR: 166,181 bytes. SHA-256: `ac976afc7023b7c9211b01f5396d287f4c41ea488c4b03f6b4ff7e6e66ddf9b1`.
