# NordGuard 0.5.0-rc.7 — bounded arrival allowance

Experimental candidate for Minecraft 26.2, Paper and Folia, Java 25. Stage 4 remains open. Keep movement checks in OBSERVE until the remaining native-client release gates pass.

## Changes

An unchanged owner position can precede several catch-up movement samples. NordGuard now repays existing speed debt first, then carries the unused allowance into arriving movement. The reserve is capped at `movement.burst-ticks` times the current speed allowance. Each batch spends only what it needs.

Long idle cannot grow the reserve beyond that cap. A speed decrease clamps it, and resets and medium transitions clear it. Client MOVE and TickEnd counts do not create allowance. This tolerates a bounded short burst after idle; it is not a packet-by-packet physics simulator. Existing evidence and burst thresholds have not been increased.

The change adds two doubles per session and constant-time arithmetic. It adds no world scan, scheduler, packet callback or dependency. This is a source-level bound, not a measured production performance comparison or proof of 600-player capacity.

## Validation and limits

Local clean verification passed all 241 unit tests, including seven new arrival-budget regressions. Runtime matrix results and failed intermediate runs are recorded in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md). The final rc.7 archive still needs its complete native and synthetic runtime matrices; do not substitute results from an earlier archive.

Ordinary packet prediction remains opt-in and observation-only. Its native accepted-frame coverage, special movement physics and distributed CPU/allocation/network behavior remain unvalidated. Existing configurations remain compatible. No automatic bans or kicks were added, and production data were not changed.

Local candidate JAR: 166,146 bytes. SHA-256: `188c725a84985ec75bd55f137ebb3b0ecbddda5ded195ab944f4c229a3aaa74c`. No new stable release is published.
