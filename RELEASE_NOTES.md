# NordGuard 0.5.2

Minecraft 26.2 on Paper and Folia; Java 25 required.

## Fixes

- Recognize coalesced landing/takeoff transitions when net displacement is still
  downward. Keep only the previous server-observed floor when the current scan
  cannot reach it, and require a bounded descending path plus a matching takeoff
  height. No extra terrain query is made.
- Count the minimum jump interval in trusted owner ticks across transport gaps,
  not just received movement samples. Client packet counts grant no extra time.
- Keep unpaid burst debt out of clean return anchors, but add SPEED evidence
  only for fresh excessive displacement rather than legal debt repayment.

No new dependency, repeating task or world scan was added. Existing schema,
permissions, configuration and data remain compatible. All checks default to
OBSERVE; an existing CORRECT setting is not reset automatically. Experimental
replica/prediction remain disabled by default; there are no automatic bans/kicks.

## Verification

284 unit tests passed, including ledge phases, skipped ground snapshots,
unknown-floor rejection, excess speed, hover and preserved fall distance.
The final archive passed 22 focused native Folia checks with Wurst 7.56.1,
including the one-block descent at 0/100/300 ms per direction, all twelve
hostile movement cases and two lifecycle cycles. This was a focused run,
not a repeat of the full ordinary/predictor or extended terrain matrix.
The same archive passed 94 Paper runtime assertions, including sustained cheat
corrections, ordinary sprint-jumps, mining/action gates and cleanup.
Full evidence and retained failed development runs are recorded in TESTING.md.

Archive: 170,865 bytes. SHA-256:
`01df68780e98941eb84249ab6fe7ad1fbaa14b0e0c9e1281d5144c5ff872a1ac`.

## Installation

Stop the server and keep a backup of the old JAR and `plugins/NordGuard`.
Remove the old NordGuard JAR from active `plugins` and keep exactly one:
`NordGuard-0.5.2.jar`. Preserve existing configuration and data, then restart.
Do not restart with both the old and new JAR present. Staging a new JAR alongside
the old one does not mean installation has been completed.

## Scope

This release targets ordinary sprint-jump transitions. BoatFlight/vehicle
physics and enforcement of the delay between consecutive mined blocks are
not added. A client module appearing enabled does not by itself prove a
server-side bypass. This is not a universal bypass audit or evidence of
600-player capacity. Full combat, elytra physics and production-plugin
interactions remain outside the verified scope. See
[TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md)
for current results, retained failed runs and archive hashes, and
[COVERAGE.md](https://github.com/NetGraniz/NordGuard/blob/main/COVERAGE.md) for check scope.
