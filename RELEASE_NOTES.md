# NordGuard 0.5.1

Minecraft 26.2 on Paper and Folia; Java 25 required.

## Fixes

- Flight checks a bounded cumulative descending envelope instead of comparing
  a multi-step displacement with a one-step velocity. Client packet counts do not
  buy time; server attribute changes do not restart the envelope clock.
- NoSlow accounts for batched item-use movement with bounded owner-time debt and
  credit. Ending use does not erase unpaid excess or mint another reserve.
- Successful setbacks seed movement history and owner position from the checked
  destination so late movement during grace remains measurable.
- Diagnostic Ping dispatch permits one queued/executing task per channel and
  discards a queued probe older than 500 ms.
- WallHit compares exact geometry for the same world and target, expires gaps
  above 200 ms and resets unknown/skipped observations. These are repeated
  observations, not continuous block-history reconstruction.
- The shutdown flag resets on enable. The local load fixture checks the actual
  connected population after an early stop instead of always expecting 50.

No new dependency, repeating task or world scan was added. Existing schema,
permissions, configuration and data remain compatible. All checks default to
OBSERVE; an existing CORRECT setting is not reset automatically. Experimental
replica/prediction remain disabled by default; there are no automatic bans/kicks.

## Verification

276 unit tests and seven session-count cases passed. The final archive passed
94 runtime assertions on each of Paper and Folia, then 32 native Folia checks
including actual Wurst movement and two disable/enable cycles. An earlier native
run lacked initial chunk/predictor coverage; the unchanged JAR passed the repeat.
That timing-sensitive experimental bootstrap remains a recorded limitation,
not a claim of stable predictor coverage. Extended/600-player tests were not
repeated. Full evidence and failed runs are retained in TESTING.md.

Archive: 170,659 bytes. SHA-256:
`c2ca78c6994a6366fd678327108032541644b0751413d057c72166126ebb7946`.

## Installation

Stop the server and keep a backup of the old JAR and `plugins/NordGuard`.
Remove the old NordGuard JAR from active `plugins` and keep exactly one:
`NordGuard-0.5.1.jar`. Preserve existing configuration and data, then restart.
Do not restart with both the old and new JAR present. Staging a new JAR alongside
the old one does not mean installation has been completed.

## Scope

This is a targeted audit-fix release, not new support for all client features,
a universal bypass audit, or evidence of 600-player capacity. Full combat,
elytra/vehicle physics and production-plugin interactions remain outside the
verified scope. See [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md)
for current results, retained failed runs and archive hashes, and
[COVERAGE.md](https://github.com/NetGraniz/NordGuard/blob/main/COVERAGE.md) for check scope.
