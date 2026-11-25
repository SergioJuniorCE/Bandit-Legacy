# Changelog

All notable changes to this fork are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed

- Build now uses GTNHGradle 2.0.34, Gradle 9.7.1 and a JVM 25 daemon toolchain
- Dependencies refreshed to GTNHLib 0.12.10, CodeChickenCore 1.5.1, Forgelin 2.1.0-GTNH and
  GT5-Unofficial 5.09.55.37
- Kotlin sources compile at language level 2.1, matching the Kotlin runtime Forgelin bundles
- The mod now requires `gtnhlib@[0.12.0,)` and `forgelin@[2.1.0-GTNH,)`
- Holding and releasing the key is the default activation mode, replacing the sticky 1.x default
- Releasing the key no longer cancels an action that already started, the click commits it
- The selection outline only renders faces that are not hidden by another selected block
- `/bandit executor` and `/bandit filter` accept names as well as ids, and reject invalid values

### Added

- Shaped executors: 3x3 Tunnel, Small Tunnel, Escape Tunnel and 3x3x3 Cube
- Ore dictionary block filter, so GT ore blocks of different metadata mine as one vein
- Server limits: max blocks per action, blocks per tick, cooldown, hunger exhaustion per block
- Tool rules: require a tool, skip blocks the tool cannot harvest, stop before the tool breaks
- Block blacklist and whitelist with `modid:name`, `modid:*` and `oredict:name` entries
- Shaped modes skip tile entity blocks unless they are the block the action started on
- Right-click crop harvesting with optional replanting
- Right-click hoe tilling of a small area
- HUD shows the selection size and the server limit, and reports refused actions
- Client settings for outline block limit, alpha, line width and the block counter
- `/bandit mode` and `/bandit limits` commands

### Fixed

- Large-scan scanned the whole world height instead of the configured vertical radius
- Precalculated selections crashed when the player disconnected while the preview was being synced
- The filter listing used the wrong translation key
