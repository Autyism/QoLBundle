<p align="center"><img src="docs/icon_transparent.png" width="128" alt="icon"></p>
<h1 align="center">QoL Bundle</h1>
<p align="center">39 client-side quality-of-life modules in one mod, each with its own switch and settings.</p>
<div align="center">

<p align="center">-><a href="docs/README.cn.md">简体中文</a><-</p>
<p align="center">-><a href="docs/README_detailed.md">Detailed</a><-</p>


![Minecraft 1.21.11 | 26.1–26.3](https://img.shields.io/badge/Minecraft-1.21.11_%7C_26.1--26.3-62B47A) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue)

</div>

## Some Features

| HUD and info |Description|
|:---|:---|
|**Sound compass**|Shows direction of sound source|
|**Throw Landing Preview**|When holding projectile, shows if landing area is safe.|
| **Fall Damage Preview** | Displays upcoming fall damage and alerts if fatal. |
| **Lava Safety Net** | Warns of nearby lava and guides you to safety. |
| **Escape Trail** | Visualises your path to help you retreat when damaged. |
| **Portal Calculator** | Previews cross-dimension coordinates and links for visible portals. |
| **Elytra Dashboard** | Shows flight telemetry, rocket count, and predicted landing spots. |
| **Placement Master** | Provides a block preview with the block status, and lock placement orientation. |
| **Chest Memory** | Tracks container contents and guides you to items. |
| **Recipe Book Helper** | Tracks missing ingredients and locates their storage. |
| **Opponent Gear Panel** | Displays a looked-at player's gear, enchantments, and active actions. |
| **Approach Alert** | Sounds and points toward players getting within 12 blocks. |
| **Being Watched** | Warns you if a player targets you for over 3 seconds. |
| **Redstone Diagnostics** | Scans machines to map signals, detect bottlenecks, list components, track item outputs, and view single-layer slices. |

<br>

| Grey zone (off by default) | Description |
| :--- | :--- |
| **AFK Clicker (F7)** | Automates clicking/holding; stops when hurt. Has presets. |
| **Freecam (F6)** | Free-flying camera with markers; ends if hurt. |
| **Elytra Take-off (V)** | Jumps, opens elytra, and fires rocket with one key. |
| **Water & Lava Vision** | Clears underwater fog and improves lava visibility. |
| **Rear-view Mirror** | Shows a live rear view but cuts frame rate in half. |


### X-ray add-on

X-ray is deliberately not part of QoL Bundle as many servers ban X-ray. You can download it seperately. When installed with the QoL Bundle, the module appears in the settings screen.
## Screenshots

![Info HUD, respawn point line and free slot counter](docs/images/hud-overview.png)

Info HUD in the top-left corner (coordinates, facing, FPS, in-game time) with the Bed & Respawn Point Keeper line below it, here warning that the recorded bed is gone. Bottom right: the free inventory slots counter of the Armor Durability HUD.

![Freecam](docs/images/freecam.png)

Freecam: the camera flies away from your body; the banner shows the key to leave and how far you are from your body.

![Freecam marker seen from the body](docs/images/freecam-marker.png)

Back in your body, a pink arrow and a beam lead to a spot you marked while flying.

![Freecam in a dark cave](docs/images/freecam-cave.png)

Freecam lights up dark caves while you fly.

## How to use

* Press **K** during gameplay, or navigate via [Mod Menu](https://modrinth.com/mod/modmenu) to open the settings screen.
* Use the switches, sliders, buttons to customize options.
* All changes save automatically.

**Share your settings**
1. Click **Copy share code** at the bottom of the screen. All settings can be copied from one line of text starting with `QOL1:`.
2. To import, click **Import share code**, then confirm.


Settings are stored in `config/qolbundle.json`. If that file is damaged, the defaults are used and the damaged file is kept as `qolbundle.json.broken`. Things remembered per world (e.g. portals seen) are stored in `config/qolbundle/worlds/`.

## Requirements

| | Version |
|---|---|
| Minecraft | Java Edition 1.21.11 or 26.1–26.3 (each version has its own jar) |
| Fabric Loader | 0.19.5 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required, for the same Minecraft version |
| Java | 21 or newer |
| [Mod Menu](https://modrinth.com/mod/modmenu) | optional, adds a settings button to the mod list (built against 17.0.1) |
| QoL Bundle: X-ray add-on | optional, separate jar; needs QoL Bundle (use the same version) |


## Compatibility

- **Sodium, Iris and shader packs:** not fully tested yet. If something looks wrong, these are the most likely places: Placement Master, Rear-view Mirror, underground view of Freecam, and the lines and walls drawn into the world.
- **Multiplayer:** so far tested in single-player only. Modules may behave differently on servers with unusual plugins.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for your Minecraft version.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for that Minecraft version and put it into your `mods` folder.
3. Download the QoL Bundle jar for your Minecraft version from the [releases](https://github.com/Autyism/QoLBundle/releases) and put it into the same `mods` folder (26.x needs Java 25).
4. Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for a settings button in the mod list.
5. Optional, only if you want X-ray: the X-ray add-on jar for the same version.
6. Start the game and press **K** in a world.

## Credits

- Made by Autyism.
- Built on [Fabric](https://fabricmc.net/) Loader and Fabric API; optional settings button through [Mod Menu](https://modrinth.com/mod/modmenu) by TerraformersMC.
- The pinyin initials table used by the item search was generated with [pypinyin](https://github.com/mozillazg/python-pinyin) (MIT License).

## License

`GPL-3.0`. QoL Bundle and the X-ray add-on are free software under the GNU General Public License v3.0: you may use, share and change them, and changed versions you share must stay under the same license. See [LICENSE](LICENSE).
