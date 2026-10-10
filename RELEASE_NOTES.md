# NordGuard 0.5.0-rc.5 — stage 4 investigation

Experimental candidate for Minecraft 26.2, Paper and Folia, Java 25. Stage 4 has not passed. This is not a stable release or a recommendation to enable movement corrections.

## Change

Skipped stationary grounded owner ticks now repay horizontal speed debt and decay SPEED evidence. They cannot accumulate future movement credit. The change adds no world query, repeating task, packet callback or dependency. Four new regressions cover ordinary batches, excessive average speed, standing-still credit and reset origins.

The separate native-client fixture drives the installed Minecraft client through normal input. A second launch enables installed Wurst Flight, SpeedHack, Spider and Jesus, verifies enablement and checks both per-check evidence and completed NordGuard corrections. Test code and third-party client binaries are not included in the server JAR.

## Known failures

Native Paper tests with TCP buffering reproduced legitimate movement triggering SPEED corrections; the earlier rc.4 also produced false FLIGHT. The stationary-tick fix does not solve arrival batching completely. The controlled relay profile explicitly disables Nagle buffering; its results are separate from the retained failing buffering profile. A passing controlled run cannot erase those failures.

Synthetic terrain edits also invalidated the optional block cache. Native prediction accepted zero frames in the initial fixtures, so zero mismatches there is not successful prediction. Ordinary prediction remains opt-in and observation-only, with its cache disabled by default.

Keep movement checks in their default OBSERVE modes. No automatic kicks or bans were added. Existing configurations and data remain compatible; production was not accessed or changed.

## Validation

Local verification and GitHub Actions run 38037584129 passed 221 unit tests without failures or skips. The identical local candidate passed the existing 90 synthetic runtime assertions on Paper and 90 on Folia.

The controlled native Paper run passed all 12 ordinary cases but deliberately skipped Wurst. The controlled native Folia run detected/corrected all 12 hostile cases and passed only 10 of 12 ordinary cases: sprint-jumping without added delay and with 300 ms/jitter triggered false SPEED. Earlier buffered Paper runs also failed legitimate movement despite detecting all four tested modules. Stage 4 remains failed; synthetic regression passes do not override those results.

Full native/synthetic results, including failures and exact test scope, are recorded in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md). These are short single-client scenarios, not a 600-player load test or exhaustive Wurst settings/bypass coverage. The CI archive differs from the tested local archive only in generated Maven metadata; its separate hash is recorded in the test report.

A stable release still needs safe treatment of legitimate batched movement, meaningful native prediction coverage, special movement/transition validation and distributed CPU/allocation/network testing.

Candidate JAR: 164,710 bytes. SHA-256: `d9b3cc6f5ac6fec3d208ba4c22e3233a3c792930d039af49035554b63af20ecf`.
