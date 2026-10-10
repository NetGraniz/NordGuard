# NordGuard 0.5.0-rc.6 — native transition fixes

Experimental candidate for Minecraft 26.2, Paper and Folia, Java 25. Stage 4 remains open. Do not treat this as a stable release or enable movement corrections on the strength of these narrow fixes.

## Changes

Owner samples can contain the first two or three native jump steps together. NordGuard now matches their cumulative height before granting bounded sprint-jump momentum. Micro-hops and implausible high takeoffs remain outside that match.

The existing collision scan also records a nearby floor without calling it current ground contact. Recent descent, a floor-crossing fall step and the jump interval gate can identify a short landing/takeoff that no owner snapshot captured. Inferred contact does not erase unaccounted fall height or establish a grounded return anchor.

When the active packet observer received neither movement nor TickEnd and the owner's airborne position is unchanged, vertical evidence pauses. Grounded idle retains its normal support/return-anchor refresh; the synthetic suite explicitly checks that cadence. Changed positions and received stationary movement still get checked. Existing speed debt is repaid, but future speed credit cannot accumulate. Sessions without an active observer retain the owner-only fallback.

These changes add bounded arithmetic and small per-session state, not another world scan, scheduler, packet-thread Bukkit read or dependency. They are not proof of 600-player capacity.

## Validation and limits

Final local verification and GitHub Actions run 38055249662 passed 234 unit tests, including thirteen new transition regressions. Native fixture runs retain failing cases; the full results and candidate hashes are in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md).

The final local archive passed all 91 synthetic runtime assertions on Paper and all 91 on Folia, including fresh idle anchors and repeated returns. Both stopped cleanly. These constructed cases do not replace native-client validation.

Before the grounded idle anchor fix, six-second ordinary cases passed 10/12 on Folia and 8/12 on Paper. Sprint-jumps passed at all three tested delays on both; remaining ordinary failures were SPEED corrections during walking or sprinting. Folia also detected and corrected all twelve actual Wurst cases (four modules at three delays). The rc.6 Paper run deliberately skipped Wurst. These matrices still fail overall and are not native validation of the final archive; that matrix must be repeated after the anchor fix.

Arrival-batched horizontal speed is not fully resolved. Native packet prediction still needs meaningful accepted-frame coverage; special movement physics and distributed CPU/allocation/network behavior also remain unvalidated. Keep all checks in their default OBSERVE modes. No automatic bans or kicks were added. Existing configurations remain compatible, and production data were not changed.

Local candidate JAR: 165,902 bytes. SHA-256: `7d60e29f92c50ac7a9e76e810ca63a73373c9462409bd6e722170926bbf1b8b7`. No new stable release is published.
