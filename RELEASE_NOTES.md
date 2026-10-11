# NordGuard 0.5.0

Minecraft 26.2 on Paper and Folia; Java 25 required.

This regular release publishes the rc.8 implementation with version metadata
updated to 0.5.0. Check logic, thresholds, permissions and configuration defaults
are unchanged. There are no automatic bans or kicks.

## Installation

Stop the server, back up the existing plugin JAR and `plugins/NordGuard`, then
replace the old NordGuard JAR with `NordGuard-0.5.0.jar`. Keep only one NordGuard
JAR in `plugins`. Keep existing configuration and data; no migration or reset is
required. Start the server and check `nordguard status` from its console.

All 17 checks default to OBSERVE. Existing CORRECT settings remain CORRECT after
the update; they are not reset automatically. Start with OBSERVE when evaluating
legitimate gameplay in a new environment. Experimental world replica and packet
prediction remain disabled by default; prediction is observation-only.

## Validation and scope

The rc.8 implementation passed 247 unit tests, 91 synthetic runtime assertions
on each platform, the corrected-fixture native client matrix on Paper and Folia,
and a narrow set of actual Wurst Flight/SpeedHack/Spider/Jesus reproductions.
Folia also passed 11 extended ordinary scenarios, including a two-minute patrol.
These checks are not a universal bypass audit.

The local Paper comparison kept approximately 20 server ticks per second with
10 and 25 moving synthetic clients, with and without NordGuard. At 50 connections
the shared-host generator lost movement cadence in both arms; that stage is not
a full-rate 50-player capacity pass. No sustained 600-player or Folia load claim
is made. Broader elytra/transitions, cross-region combat and production-plugin
interactions remain unvalidated. Publishing this release does not remove those
limits.

See [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/main/TESTING.md) for
runtime builds, archive hashes, measurements and retained development failures.
See [COVERAGE.md](https://github.com/NetGraniz/NordGuard/blob/main/COVERAGE.md) for
individual check scope. Production worlds, configurations and account data were
not accessed or changed for this release.

JAR size: 166,166 bytes. SHA-256:
`71f8aa99336c9c70539bfd5faed53cec8bb0e70ba2664c017dd34d204399d237`.
