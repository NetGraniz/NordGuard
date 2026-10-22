# 0.5.3 candidate

Not a published release. Production installation is pending validation.

The local source is based on `838484d` (0.5.2). The candidate adds configurable combat and vehicle checks plus additive configuration migration. The default configuration remains observation-only. Older missing Criticals and BoatFlight modes inherit Reach and Flight respectively; explicit modes are preserved.

Completed so far:

- 343 Java unit tests pass, including pure boat traces, mounted-body clearance, native-attack FIFO ordering, wall retention, mining work accounting and exact-byte configuration backups.
- Eight Node tests pass. The relay unit test now uses ephemeral ports rather than competing with the native fixture.
- The first extended native Folia combat matrix passed 42 gameplay cases at 0/100/300 ms per direction, including actual target-health changes, ordinary and enchanted jumping criticals, prior plugin cancellation, extra damage and packet-observer-disabled wall checks. Startup, actual Wurst connection and internal-error checks also passed. The subsequent spatial-budget tightening still needs the same runtime coverage.

Use the test JSON's SHA-256 rather than a development filename to identify an archive. The mounted-body candidate `c938da2db71f79380cdbdf1ab94f7c6c017a0ca219b8048793fbe936ff375de4` passed the focused native Folia BoatFly test: OBSERVE allowed a 27.24-block ascent; CORRECT limited the observed maximum to 4.94 blocks and completed five vehicle corrections. The later vehicle-freshness candidate `9c358a915cd57fe448ae4a07e0997e3b0e1cc3816f2d64f0f913d1a4cd724c1b` passed on Paper: the same 27.24-block positive control, 9.36-block corrected maximum and three completed corrections. The same player, boat and second passenger remained together on both platforms.

Retained vehicle development failures include an incorrectly downward test bubble column, a native upward column with server velocity 2.7 but no client ascent, an unexercised server-only velocity control, a strict floating-point settings readback failure, and the pre-fix seated-feet clearance rejection. Those are not passing launch controls. The current impulse deferral uses owner-side server velocity; the vehicle packet handler was inspected and does not set that velocity from client displacement. Bubble/impulse launch coverage is not established by the native fixture.

Paper combat passed the ordinary and hostile controls at 0/100 ms. At 300 ms, four actual Packet Criticals critical events were cancelled while one ordinary noncritical hit remained allowed; the original zero-total-damage assertion was too broad. A later Sharpness control had four uncancelled legitimate criticals, but the item no longer had Sharpness at the end, so that enchanted precondition was not established. Neither is evidence that legitimate hits were cancelled.

Native mining confirmed a real FastBreak cadence advantage without duplication: eight primary blocks produced eight drops, with no lower blocks removed. The first work-budget candidate slowed first-to-last accepted dirt from 4.404 to 6.443 seconds and wooden-pick stone from 5.784 to 9.233 seconds at zero delay; ordinary mining had no cancellations. At 300 ms, an arbitrary 15% relative-slowdown assertion failed despite a sequence inside the actual absolute work budget. The test now checks that configured work contract instead of a noisy percentage. Rejected-start history also covers delayed retries that outlive a newer start.

Remaining runtime runs cover the newest archive, high-delay mining, instant/Creative controls and lifecycle behavior. No sustained 600-player capacity or full cheat-client coverage is claimed.

Test helpers use synthetic data on loopback-only servers. Wurst is supplied locally and is not redistributed. No production configuration, player identity, authorization data or personal launcher account is part of this repository.
