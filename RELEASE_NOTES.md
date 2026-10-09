# NordGuard 0.5.0-rc.2

Release candidate for Minecraft 26.2, Paper and Folia, Java 25. Not a stable complete anticheat.

## Scope

The existing 17 movement, combat and block checks retain their behavior and default to OBSERVE. There are no automatic kicks or bans. Existing configurations and data are not overwritten. Production was not accessed or modified.

Stage 1 adds bounded acknowledgement-based world state. The ordinary movement predictor remains a standalone prototype, not a runtime check. There is no new packet-based movement correction or complete latency-compensated client simulation. The remaining gates are listed in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/v0.5.0-rc.2/TESTING.md).

## Changes

- Separate world journal: changes wait for an ordered matching Pong. Each barrier commits only its recorded sequence, never newer updates.
- Dirty chunks remain unknown to the geometry lookup until their updates are confirmed. Unaffected confirmed chunks remain usable.
- Dimension height and key come from the observed reset packet. Loss, ambiguity, failed world writes, expired/reordered replies and capacity exhaustion discard uncertain knowledge.
- Journal capped at 16 updates / 512 KiB and four barriers. Inbox and confirmed cache retain their separate limits. Global copying, decoding and materialization budgets remain in force.
- World synchronization diagnostics in `/nordguard inspect <player>`. The cache remains opt-in: `packets.world-replica: false`.

Pong does not prove client obedience. Unknown data do not become air or trigger punishment. This is a conservative acknowledged prefix, not a replay of every possible client-world branch. It does not force chunk loading or resend missing chunks.

## Validation

177 unit tests passed locally. Runtime results and remaining validation limits are recorded in [TESTING.md](https://github.com/NetGraniz/NordGuard/blob/v0.5.0-rc.2/TESTING.md). Synthetic model workloads are not evidence of capacity at 600 online players.

The JAR contains no test probes, worlds, logs, player data or bundled Netty dependency.

SHA-256: `626ef3e0da7c0b54b5faaa2b30ea4b89860716c4c521a0e360a26af8349f2a6b`
