# Bandit Legacy

Get your ores like a Bandit.

A maintained fork of [ElytraServers/Bandit-Legacy](https://github.com/ElytraServers/Bandit-Legacy), a vein mining mod for
Minecraft 1.7.10, brought up to the current GTNH build standard and extended with the things players expect from
[FTB Ultimine](https://github.com/FTBTeam/FTB-Ultimine): shaped mining, server side limits, an ore dictionary aware
filter, right-click crop harvesting and a readable block preview.

## Install

Requires **Forgelin 2.1.0+**, **GTNHLib 0.12+** and **CodeChickenCore**.

Supported on **GTNH 2.9.x** and the 2.10 nightlies. Older packs are not supported.

## What this fork changes

### Build and dependencies

- GTNHGradle `2.0.34` on Gradle `9.7.1` with a JVM 25 daemon toolchain (`gradle/gradle-daemon-jvm.properties`)
- GTNHLib `0.12.10`, CodeChickenCore `1.5.1`, Forgelin `2.1.0-GTNH`, GT5-Unofficial `5.09.55.37` for the dev runs
- Kotlin is compiled at the 2.1 language level, which is the runtime Forgelin ships
- The GTNHLib 0.5/0.12 `BlockPos.offset` ABI workaround is gone, since the current API returns `BlockPos`

### Mining modes

Vein executors follow a connected cluster of matching blocks, shaped executors dig a fixed geometry. Shaped modes only
break blocks the held tool can harvest, and they never touch a tile entity block unless it is the same block the player
started on, so a tunnel cannot eat a machine wall by accident. Switch at runtime with `/bandit executor`.

| id | Executor         | Kind  | Description                                                          |
|----|------------------|-------|----------------------------------------------------------------------|
| 0  | Manhattan        | vein  | connected neighbours within the configured radius                    |
| 1  | Manhattan+       | vein  | additionally checks the 3x3x3 around every candidate                 |
| 2  | Large-scan       | vein  | everything matching within a cube, for veins that are not connected  |
| 3  | Manhattan(L)     | vein  | Manhattan+ with the large radius                                     |
| 4  | Tunnel           | shape | 3x3 tunnel along the direction you are looking                       |
| 5  | Small Tunnel     | shape | 1x2 tunnel along the direction you are looking                       |
| 6  | Escape Tunnel    | shape | 1x2 tunnel that rises one block per block                            |
| 7  | 3x3x3 Cube       | shape | cube around the block you broke, mined from the inside out           |

### Block filters

| id | Filter                        | Description                                                        |
|----|-------------------------------|--------------------------------------------------------------------|
| 0  | Matching Block                | same block id, metadata ignored                                     |
| 1  | Matching Block and Metadata   | same block id and metadata                                          |
| 2  | All                           | everything the scan touches                                         |
| 3  | Matching Ore Dictionary       | same ore dictionary entry, the GTNH answer to Ultimine's `forge:ores` |

### Limits and costs

The server decides how much one action may do, the client cannot raise it:

- `max-blocks` caps a single action, and the preview already respects it
- `blocks-per-operation` spreads the work over server ticks
- `cooldown-ticks` spaces actions out
- `exhaustion-per-block` charges hunger per harvested block, the GTNH way of paying for the convenience
- `require-tool`, `require-valid-tool-for-block` and `prevent-tool-break-at` decide what may be used and when it stops

### Right-click actions

With the vein mining key held down:

- right-click a fully grown crop to harvest the connected patch of the same crop, and replant it when the player has the
  matching seed
- right-click dirt or grass with a hoe to till a small area around the clicked block

### Preview and HUD

- the outline only draws faces that are not covered by another selected block, which keeps a 300 block ore vein readable
  and cheap to draw
- outline block limit, alpha and line width are client settings
- the HUD shows how many blocks the current selection would mine and the server limit it is capped by
- refused actions now say why instead of silently doing nothing

### Activation

The key is **hold to arm, release to disarm** by default, like Ultimine. A click commits the action, so releasing the key
mid-action does not cut a vein short; use `/bandit stop` for that. `/bandit mode toggle` brings back the 1.x sticky
behaviour.

## Usage

1. Bind the **Vein Mining Trigger** key in the controls menu. There is no default binding.
2. Hold the key. The HUD shows the current executor, the filter and how many blocks the selection covers.
3. While holding the key, **Shift + scroll** cycles the executor and **Ctrl + scroll** cycles the filter.
4. Break a block to start. Right-click for crops and tilling.

`/bandit stop` halts the running action. The action is not retroactive, mined blocks stay mined.

## Commands

| Command                                     | Effect                                                       |
|---------------------------------------------|--------------------------------------------------------------|
| `/bandit executor [id\|name]`               | list or select the executor                                  |
| `/bandit filter [id\|name]`                 | list or select the block filter                              |
| `/bandit mode [hold\|toggle]`               | list or select how the key behaves                            |
| `/bandit limits`                            | show the effective server limits                              |
| `/bandit drop_pos [start\|player]`          | where harvested drops appear                                 |
| `/bandit drop_timing [immediately\|...]`    | when harvested drops appear                                  |
| `/bandit stop`                              | stop the running action                                      |
| `/bandit stop_on_release [true\|false]`     | 1.x alias for `/bandit mode hold\|toggle`                    |

## Server configuration

`config/bandit.cfg`, applied on server start.

| Key                          | Default | Meaning                                                              |
|------------------------------|---------|----------------------------------------------------------------------|
| `executor.manhattan-radius`  | 8       | scan radius of the connected-vein executors                           |
| `executor.manhattan-large-radius` | 16 | scan radius of Manhattan(L)                                          |
| `executor.large-scan-radius-xz`   | 32 | horizontal radius of Large-scan                                      |
| `executor.large-scan-radius-y`    | 32 | vertical radius of Large-scan                                        |
| `executor.tunnel-length`     | 16      | how many blocks the tunnel executors dig                              |
| `executor.cube-radius`       | 1       | radius of the cube executor                                          |
| `limits.max-blocks`          | 1024    | hard cap of blocks per action                                        |
| `limits.blocks-per-operation`| 64      | blocks harvested per server tick                                     |
| `limits.cooldown-ticks`      | 0       | ticks between two actions, 0 disables the cooldown                    |
| `costs.require-tool`         | true    | only mine while holding an item with durability                       |
| `costs.require-valid-tool-for-block` | true | skip blocks the tool cannot harvest                              |
| `costs.prevent-tool-break-at`| 1       | stop when the tool has this much durability left, 0 disables it       |
| `costs.exhaustion-per-block` | 0.0     | hunger exhaustion per harvested block                                 |
| `matching.block-blacklist`   | empty   | blocks that can never be mined, `modid:name` or `oredict:oreIron`      |
| `matching.block-whitelist`   | empty   | if set, only these blocks can be mined                                |
| `matching.shaped-skip-tile-entities` | true | shaped modes leave tile entity blocks alone unless they match the start block |
| `features.right-click-harvest` | true  | crop harvesting on right-click                                        |
| `features.right-click-replant` | true  | replant harvested crops when seeds are available                      |
| `features.right-click-till`  | true    | hoe tilling on right-click                                            |
| `features.till-radius`       | 1       | radius of the tilled area                                             |

## Client configuration

`config/bandit-client.cfg`, editable in the mod list config screen.

| Key                   | Default  | Meaning                                     |
|-----------------------|----------|---------------------------------------------|
| `menu-margin-x/y`     | 40       | HUD margin                                  |
| `menu-position`       | TOP_LEFT | HUD corner                                  |
| `outline-block-limit` | 512      | max blocks to draw the outline for, 0 hides it |
| `outline-alpha`       | 160      | outline transparency                        |
| `outline-line-width`  | 2.0      | outline line width                          |
| `show-block-count`    | true     | show the selection size in the HUD           |

## Building

Java 25 is the build JVM, the Gradle wrapper pins it through `gradle/gradle-daemon-jvm.properties`.

```bash
./gradlew build          # jar in build/libs
./gradlew runServer      # dev server with GT5U loaded
./gradlew runClient      # dev client
```

## Known limitations

- The XP cost per block of Ultimine is not implemented; hunger exhaustion is the supported cost knob.
- Shaped mining is opt-in. The default executor stays the connected-vein one, because that is what the mod is for.
- Server config changes need a restart, the block lists are cached.

## Credits

Original mod by Taskeren and the ElytraServers contributors. This fork keeps the same MIT license.
