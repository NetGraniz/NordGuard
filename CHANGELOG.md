# Changelog

## 0.1.1

- Preserve the last clean supported return position during temporary evidence resets.
- Require displacement evidence before the first post-reset position can replace the return point, preventing repeated ground-speed attempts from moving it forward.
- Keep the validated destination after a successful NordGuard setback; use two settling ticks rather than the configured external-transition grace.
- Discard the old return position on external teleports, respawns, world changes and game-mode changes. Ignore stale correction completions after a new origin transition.
- Track the server-issued teleport sequence as a fallback for external asynchronous teleports on Folia. Reject a correction completion if another server teleport has intervened.
- Add runtime regressions for immediate repeated flight, repeated speed corrections, external teleport handling and ordinary walking.
- Keep existing check modes, permissions and configuration schema. All checks still default to OBSERVE.

## 0.1.0

- Initial movement and NoFall preview for Minecraft 26.2 Paper and Folia.
