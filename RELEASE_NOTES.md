# NordGuard 0.5.6

Minecraft 26.2 on Paper and Folia; Java 25 required.

Fix false Speed evidence when the player's region runs below 20 TPS. Movement
budgets now account for monotonic time between owner samples: a 125 ms interval
receives 2.5 nominal ticks, not one. Catch-up calls receive only their elapsed
fraction. Idle credit remains burst-bounded; packets cannot mint time.

NoSlow and medium displacement checks use the same timing. Jump grace and bounded
grouped steps account for slow sampling without doubling the normal step allowance
for minor healthy-tick jitter. Speed, Flight and Spider are not globally disabled.

New setting: `movement.max-elapsed-ticks: 4` (range 2–6). Larger intervals, or gaps
beyond the independent `max-sample-gap-ms`, defer history rather than banking an
unlimited allowance. Keep `movement.max-sample-gap-ms: 250` for low-TPS testing;
100 ms is an interruption threshold, not compensation. Existing explicit values
and modes remain unchanged. Startup adds missing keys through the existing
validated, backed-up configuration migration; reload does not write the file.

No new production task, world scan, dependency or external service. The test-only
owner stall helper is not included in the plugin. These are bounded movement
envelopes, not complete physics or a capacity guarantee for 100/600 players.

Local Maven verification: 371 Java tests passed; eight Node tests passed. GitHub
CI for source `f912e0d` passed: run `40007891647`. Native runtime verification is
recorded in TESTING.md; use that record for exact scope and archive identity.
The final archive passed six clean-client movement cases at roughly 14/8 TPS;
actual Wurst SpeedHack, Flight and Spider received corrections at roughly 8 TPS.

Stop the server before replacing the old NordGuard JAR. Keep the existing
`plugins/NordGuard` directory. Do not leave duplicate NordGuard JARs in `plugins`.

Archive: 219,842 bytes. SHA-256:
`d8a7cb2533f4742153f3efd9f1fd1619e98e5006abf74feade4897cb4dbaf314`.

---

# NordGuard 0.5.5

Minecraft 26.2 on Paper and Folia; Java 25 required.

Fix false Flight corrections during diagonal downhill sprint-jumps over one/two-block
steps. A bounded history of server-observed support distinguishes missed contacts
from continued flight without a wall exemption or a global threshold increase.
Validated descending contacts split fall segments without erasing prior fall debt.

After a server velocity event, NoFall defers a new uncertain fall segment until
supported contact is established. Client ground flags alone cannot restore trust.
This avoids false corrections after impulses but leaves that uncertain segment
outside NoFall enforcement; it is not a complete knockback physics simulator.

No configuration keys or defaults change. No new world scan, repeating task or
dependency is added. Existing configuration and player data stay in place.

Verification: 359 Java tests, eight Node tests, 104 Paper runtime checks and 14
native Folia checks passed. The exact release JAR completed three honest diagonal
descent routes with 300 ms added delay per direction and ordered jitter without
violations or corrections. Actual Wurst Flight, SpeedHack, Spider and Jesus still
received corrections at zero added delay. See TESTING.md for scope and limitations;
these tests do not establish 600-player capacity.

Stop the server before replacing the old NordGuard JAR. Keep the existing
`plugins/NordGuard` directory and do not leave duplicate NordGuard JARs in `plugins`.

Archive: 218,330 bytes. SHA-256:
`77bc2b6c782d7868adfbd7d9eade92efccf4f55ba2cc1e7fb6cd5fe8cee8274d`.

---

# NordGuard 0.5.3

Minecraft 26.2 on Paper and Folia; Java 25 required.

## Fixes

- Keep WallHit confirmation between ordinary weapon-cooldown attacks, with bounded
  owner rescans and a packet-observer-disabled fallback.
- Cancel confirmed invalid Packet Criticals hits using ordered native attack evidence
  and exact solid support. Keep ordinary hits, genuine jumping criticals, enchantments
  and other plugins' damage/cancellation decisions intact.
- Add dry-air BoatFlight checks and root-boat corrections that preserve passengers.
  Validate rider head/side space without treating seated feet as a standing-player
  collision with the floor.
- Constrain repeated non-instant FastBreak using amortized full work and pause costs.
  Retain four rejected starts across delayed native retries. Instant and Creative
  mining remain exempt.
- Add editable combat, vehicle and mining limits, native vehicle-packet freshness,
  and startup-only additive configuration migration with exact-byte backups.
- Retain the sprint-jump ledge and stale Speed-evidence fixes from 0.5.2.

No new dependency or repeating task is added. Boat and strict Criticals terrain
probes share the existing spatial budget. Mining work accounting adds no per-tick
world query. Experimental replica/prediction remain disabled by default.
There are no automatic bans or kicks.

## Verification

343 Java unit tests and eight Node tests passed. The exact final archive passed
29 native Folia checks with Wurst 7.56.1, 94 Paper runtime assertions, and a separate
12-check Paper mining run at 300 ms per direction with FIFO jitter. Ordinary,
instant and Creative mining controls remained unflagged. Boat corrections preserved
the original player, boat and second passenger. A private copy of the production
configuration gained 24 missing keys without changing its effective policy; its
backup was byte-exact and a second migration made no change.

Development failures, partial runs and unexercised launch controls are recorded
in TESTING.md and CANDIDATE.md. These checks do not establish 600-player capacity.

Archive: 210,604 bytes. SHA-256:
`d38ef0b22277ba7cacec4a4ff64a0c48b2ead8b019057baaca196baa5b8be794`.

## Installation

Stop the server and keep a backup of the old JAR and `plugins/NordGuard`.
Remove the old NordGuard JAR from active `plugins` and keep exactly one:
`NordGuard-0.5.3.jar`. Preserve existing configuration and data, then restart.
Do not restart with both the old and new JAR present. Staging a new JAR alongside
the old one does not mean installation has been completed.

## Scope

Missing keys are added on startup, not reload. Existing explicit values/modes
remain intact. Missing Criticals and BoatFlight modes inherit Reach and Flight
respectively; fresh installs keep all 19 checks in OBSERVE. Invalid reloads retain
the running policy. Use `nordguard reload` after editing supported thresholds.

The first WallHit settling hit may land. Silent legal-looking aim is not proof
of KillAura. Ordinary noncritical hits are not cancelled merely because a client
module is enabled. Boat coverage is dry-air vertical motion, not horizontal speed.
Native bubble/impulse launches were not exercised successfully; those contexts
defer rather than claim full validation. Mining has bounded burst slack, not a
complete digging-protocol simulator or general duplication protection. See
[TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md)
for current results, retained failed runs and archive hashes, and
[COVERAGE.md](https://github.com/NetGraniz/NordGuard/blob/main/COVERAGE.md) for check scope.
