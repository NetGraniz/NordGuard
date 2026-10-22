# 0.5.3 candidate

Not a published release. Production installation is pending validation.

The local source is based on `838484d` (0.5.2). The candidate adds configurable combat and vehicle checks plus additive configuration migration. The default configuration remains observation-only. Older missing Criticals and BoatFlight modes inherit Reach and Flight respectively; explicit modes are preserved.

Completed so far:

- 333 Java unit tests pass, including pure boat traces, mounted-body clearance, native-attack FIFO ordering, wall retention and exact-byte configuration backups.
- Eight Node tests pass. The relay unit test now uses ephemeral ports rather than competing with the native fixture.
- The first extended native Folia combat matrix passed 42 gameplay cases at 0/100/300 ms per direction, including actual target-health changes, ordinary and enchanted jumping criticals, prior plugin cancellation, extra damage and packet-observer-disabled wall checks. Startup, actual Wurst connection and internal-error checks also passed. The subsequent spatial-budget tightening still needs the same runtime coverage.

The final local candidate JAR is 205,574 bytes before the mounted-body fix; use the test JSON's SHA-256 rather than a development filename to identify an archive. The newer mounted-body candidate has SHA-256 `c938da2db71f79380cdbdf1ab94f7c6c017a0ca219b8048793fbe936ff375de4` and passed the focused native Folia BoatFly test: OBSERVE allowed a 27.24-block ascent; CORRECT limited the observed maximum to 4.94 blocks and completed five vehicle corrections. The same player, boat and second passenger remained together.

Retained vehicle development failures include an incorrectly downward test bubble column, a native upward column with server velocity 2.7 but no client ascent, an unexercised server-only velocity control, a strict floating-point settings readback failure, and the pre-fix seated-feet clearance rejection. Those are not passing launch controls. The current impulse deferral uses owner-side server velocity; the vehicle packet handler was inspected and does not set that velocity from client displacement. Bubble/impulse launch coverage is not established by the native fixture.

Paper combat/vehicle and native mining measurements are still running. No sustained 600-player capacity or full cheat-client coverage is claimed. FastBreak's inter-block delay removal is not yet a confirmed premature server-side break.

Test helpers use synthetic data on loopback-only servers. Wurst is supplied locally and is not redistributed. No production configuration, player identity, authorization data or personal launcher account is part of this repository.
