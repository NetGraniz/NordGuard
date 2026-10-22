# 0.5.3 candidate

Not a published release. Production installation is pending validation.

The local source is based on `838484d` (0.5.2). The candidate adds configurable combat and vehicle checks plus additive configuration migration. The default configuration remains observation-only. Older missing Criticals and BoatFlight modes inherit Reach and Flight respectively; explicit modes are preserved.

Completed so far:

- 330 Java unit tests pass, including pure boat traces, native-attack FIFO ordering, wall retention and exact-byte configuration backups.
- Eight Node tests pass. The relay unit test now uses ephemeral ports rather than competing with the native fixture.
- The first extended native Folia combat matrix passed 42 gameplay cases at 0/100/300 ms per direction, including actual target-health changes, ordinary and enchanted jumping criticals, prior plugin cancellation, extra damage and packet-observer-disabled wall checks. Startup, actual Wurst connection and internal-error checks also passed. The subsequent spatial-budget tightening still needs the same runtime coverage.

Vehicle/passenger corrections and native mining measurements are being tested separately. The pure-model tests do not establish safe runtime boat correction. No sustained 600-player capacity or full cheat-client coverage is claimed. FastBreak's inter-block delay removal is not yet a confirmed premature server-side break.

Test helpers use synthetic data on loopback-only servers. Wurst is supplied locally and is not redistributed. No production configuration, player identity, authorization data or personal launcher account is part of this repository.
