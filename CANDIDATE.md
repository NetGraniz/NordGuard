# Development record

## 0.5.6 timing test development

- The first low-TPS native run stopped at a harness assertion: `marker()` returns
  output text, not regex captures. The runner interpreted a string character as
  the measured interval. The server log actually recorded 70.25 ms owner ticks.
  This run is not counted as a passing runtime matrix.
- Candidate `8711fdf496acdeb4e9c22ff99cb923788a4c52f66c2c60b2367aef3f33cdeb84`
  then passed six native ordinary and three Wurst low-TPS cases. A subsequent
  review tightened the healthy-tick HighJump step rounding so a 51 ms interval
  cannot double a one-step allowance. The final archive has a different hash;
  only its own recorded rerun is release evidence.

## 0.5.5 retained failures

- Candidate `ad009e97b3ab1c90b5bcee7c58332c9fafeea12b67ff2dd928d4e44ca40f6a4d`
  removed the diagonal Flight failures, but a 300 ms alternating-step route
  produced a false NoFall. Repeated focused routes reproduced the NoFall;
  they are not a full passing matrix. The combined native SnowShoe OBSERVE
  positive control also failed to accumulate evidence; that control is not
  counted as passing.
- Candidate `67d0c213ecb02d9dfcc3f2e3e5878aa029ff656b276fabd87938e817df7ab837`
  added exact swept contacts. Flight stayed clear, but two 300 ms routes
  still produced false NoFall evidence when contact and subsequent movement
  were coalesced.
- Candidate `919d6cbd3293c2f424619479f29ca1d04c8cba372644dff5ce9dee3fd7badc4d`
  added bounded ordinary-gravity walkoff matching. The eight-route run
  passed all Flight controls but retained one NoFall failure at 15 degrees
  and 300 ms, even after native-client terrain synchronization. A separate
  fresh 15-degree run passed; it does not erase the retained failure.
- The following change treats a post-server-velocity fall origin as
  untrusted until geometric or validated inferred contact. This is an
  explicit conservative NoFall deferral, not complete post-knockback physics
  prediction. Native collision-field diagnostics in PlayerMoveEvent did not
  provide useful contacts and were not added to the production plugin.

## 0.5.3 development record

Retained development evidence. See TESTING.md and RELEASE_NOTES.md for the final release archive and completed checks.

The local source is based on `838484d` (0.5.2). The candidate adds configurable combat and vehicle checks plus additive configuration migration. The default configuration remains observation-only. Older missing Criticals and BoatFlight modes inherit Reach and Flight respectively; explicit modes are preserved.

Checks recorded during development:

- 343 Java unit tests pass, including pure boat traces, mounted-body clearance, native-attack FIFO ordering, wall retention, mining work accounting and exact-byte configuration backups.
- Eight Node tests pass. The relay unit test now uses ephemeral ports rather than competing with the native fixture.
- The first extended native Folia combat matrix passed 42 gameplay cases at 0/100/300 ms per direction, including actual target-health changes, ordinary and enchanted jumping criticals, prior plugin cancellation, extra damage and packet-observer-disabled wall checks. Startup, actual Wurst connection and internal-error checks also passed. The subsequent spatial-budget tightening still needs the same runtime coverage.

Use the test JSON's SHA-256 rather than a development filename to identify an archive. The mounted-body candidate `c938da2db71f79380cdbdf1ab94f7c6c017a0ca219b8048793fbe936ff375de4` passed the focused native Folia BoatFly test: OBSERVE allowed a 27.24-block ascent; CORRECT limited the observed maximum to 4.94 blocks and completed five vehicle corrections. The later vehicle-freshness candidate `9c358a915cd57fe448ae4a07e0997e3b0e1cc3816f2d64f0f913d1a4cd724c1b` passed on Paper: the same 27.24-block positive control, 9.36-block corrected maximum and three completed corrections. The same player, boat and second passenger remained together on both platforms.

Retained vehicle development failures include an incorrectly downward test bubble column, a native upward column with server velocity 2.7 but no client ascent, an unexercised server-only velocity control, a strict floating-point settings readback failure, and the pre-fix seated-feet clearance rejection. Those are not passing launch controls. The current impulse deferral uses owner-side server velocity; the vehicle packet handler was inspected and does not set that velocity from client displacement. Bubble/impulse launch coverage is not established by the native fixture.

Paper combat passed the ordinary and hostile controls at 0/100 ms. At 300 ms, four actual Packet Criticals critical events were cancelled while one ordinary noncritical hit remained allowed; the original zero-total-damage assertion was too broad. A later Sharpness control had four uncancelled legitimate criticals, but the item no longer had Sharpness at the end, so that enchanted precondition was not established. Neither is evidence that legitimate hits were cancelled.

Native mining confirmed a real FastBreak cadence advantage without duplication: eight primary blocks produced eight drops, with no lower blocks removed. The first work-budget candidate slowed first-to-last accepted dirt from 4.404 to 6.443 seconds and wooden-pick stone from 5.784 to 9.233 seconds at zero delay; ordinary mining had no cancellations. At 300 ms, an arbitrary 15% relative-slowdown assertion failed despite a sequence inside the actual absolute work budget. The test now checks that configured work contract instead of a noisy percentage. Rejected-start history also covers delayed retries that outlive a newer start.

One high-delay stone trial already took 10.66 seconds between its first and last
accepted blocks, above the 9.20-second budget lower bound. Requiring a rejection
merely because FastBreak was enabled was incorrect. The final tests require the
actual configured work contract and only demand rejection for an exercised excess.

The final archive passed 29 native Folia checks, 94 Paper runtime assertions and
12 separate high-delay Paper mining checks. A copied production configuration
passed preservation, backup and idempotence checks. No sustained 600-player
capacity, successful native bubble/impulse launch control or full cheat-client
coverage is claimed.

Test helpers use synthetic data on loopback-only servers. Wurst is supplied locally and is not redistributed. No production configuration, player identity, authorization data or personal launcher account is part of this repository.
