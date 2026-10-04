<p align="center"><img src="docs/icon.png" width="128" alt="icon"></p>
<h1 align="center">QoL Bundle</h1>
<p align="center">39 client-side quality-of-life modules in one mod, each with its own switch and settings.</p>

<div align="center">

**English** | [简体中文](README.zh-CN.md)

![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62B47A) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue)

</div>

QoL Bundle collects many small helpers in one Fabric mod: HUD information, survival warnings, chest memory and item search, placement previews, redstone diagnostics, PvP awareness and more. Every feature is a separate module with its own on/off switch and its own settings, so you keep only what you want.

- **Client-side only.** Nothing has to be installed on the server.
- **30 modules are on** when you first start the game, **9 start off**. A module that is switched off does nothing.
- **Grey zone.** Five modules (AFK Clicker, Freecam, Elytra One-Key Take-off, Water & Lava Vision, Rear-view Mirror) automate your own key presses or give you more than the normal view. They are off by default, sit in their own group in the settings, and are meant for single-player and your own server.
- **X-ray is not included.** It is a separate, optional add-on jar (see [below](#x-ray-add-on-separate-download)).
- English and Simplified Chinese.

## Features

### HUD and information

- **Info HUD** (on by default): Coordinates, facing with its axis (for example "North (-Z)"), FPS, and the in-game day and time in a screen corner. A real-world clock can be added, and the corner and text size can be changed.
- **Armor Durability HUD** (on by default): Your worn armor and held tools with their remaining durability (uses left, percent or both), coloured from green to red. A last row with a chest icon shows how many of your 36 inventory slots are still empty.
- **Break Progress** (on by default): While you mine, a small bar under the crosshair shows how far the block is broken, with the percentage and the seconds left.
- **Villager Trade Peek** (on by default): Look at a villager or wandering trader, no click needed, to see all its trades with prices, enchanted books by name, and how often each trade can still be used. The game only sends trades when you open the trading screen, so open it once per villager; the trades are then remembered until you leave the world.
- **Sound Compass** (on by default): Small pointers on a ring around the crosshair show where recent sounds came from, labelled with the subtitle text and "(above)" or "(below)" when needed. Dangerous sounds (creeper fuse, lit TNT, Warden, fireballs) get larger red pointers. It uses the same sounds as the game's subtitles, but subtitles do not need to be on.
- **Throw Landing Preview** (on by default): Holding an ender pearl, snowball, egg, splash or lingering potion, or bottle o' enchanting draws the flight arc and marks the landing spot. Potions show the area they will reach. Pearls get a traffic light: green for a clean landing, yellow when they hit a wall, red when they hit the ceiling or land at your feet.

### Survival and safety

- **Durability Alert** (on by default): When a held tool or a worn armor piece is down to 5% durability (adjustable), the screen edges flash red, a sound plays and a line names the item and the uses left. Each item warns once, and again only after it has been repaired.
- **Fall Damage Preview** (on by default): While you fall, a line shows how many hearts the landing will cost, turns red with "FATAL" when it would kill you, and says so when a totem in your hand will save you. It follows the game's own rules, including Feather Falling, Protection, Resistance, Slow Falling, Jump Boost and soft landings such as water, hay bales, slime blocks and beds.
- **Lava Safety Net** (on by default): Orange screen edges, a sound and a warning when there is open lava a few blocks below your feet (lava under a solid floor does not count). Once you are in lava, a green arrow points to the nearest spot where you can stand. It stays quiet in creative mode and with Fire Resistance.
- **Bed & Respawn Point Keeper** (on by default): Shows where you will respawn and how far away that is, tells you whether the bed you look at is yours, and gives a big warning with a sound when your bed is gone or your respawn anchor is empty. The server never tells your game where you respawn, so the mod learns it from the game's own messages; clicking a bed records it.
- **Escape Trail** (on by default, shown after you take damage; show/hide key not bound): Quietly remembers the last 30 seconds of the way you walked. When you get hurt, an orange arrow next to the crosshair leads back along that way, around the corners you took, and the path is drawn on the ground. It can also be shown all the time, or with a key.

### Travel and navigation

- **Portal Calculator** (on by default): Look at a nether portal to see the matching coordinates on the other side, which portal it will link to (or that a new one will be built), and whether the way back returns to the same portal. It only knows portals you have seen; they are remembered for each world and server.
- **Nether Roof Helper** (on by default): On top of the Nether ceiling (Y 128 and above): a heading tape at the top of the screen, Nether and Overworld coordinates side by side, and (with Portal Calculator on) what a portal built on the spot would link to. Type an Overworld destination into its settings and the tape points the way.
- **Elytra Dashboard** (on by default): While gliding: speed, vertical speed, pitch, height above ground, rockets left, remaining elytra flight time and the predicted landing spot, which is also marked in the world. The prediction assumes you keep looking the same way and use no more rockets.

### Building and placement

- **Placement Master** (on by default; hold Left Alt to lock): Three aids for placing blocks the right way round.
  - A see-through preview of the block exactly as it will be placed, with an arrow for the way it faces and a short text under the crosshair (for example "facing north · lower half").
  - With slabs, stairs and trapdoors, the half of the face you aim at is marked, so you know whether you get the upper or the lower half.
  - While you hold Left Alt, a block that would face a different way than the last block you placed is simply not placed; the preview turns green when it matches and red when it does not.
  - It only previews and holds back clicks. What your game sends is never changed.
- **Beacon & Conduit Range** (on by default): Look at a beacon or conduit, or hold one and aim where you want to place it, to see how far its effect reaches. Beacons show their real square column (worked out from the pyramid below), conduits their sphere (worked out from the frame).

### Chat and inventory

- **Chat Enhancements** (on by default; search key not bound): Four improvements, each with its own switch.
  - Timestamps in front of every chat line.
  - A gold [@] mark, a sound and a pop-up when someone mentions your name or one of your extra keywords. Private messages to you count too; your own messages do not.
  - Chat search: a Search button in the chat screen (or a key) lists every line of this session that contains what you type.
  - Chat survives a reconnect: rejoin the same world or server and the old chat is back above a divider line, and the Up arrow still recalls what you typed. This lasts until you close the game.
- **Multilingual Item Search** (on by default): A search box in the bottom-left corner of inventory and container screens (not the creative inventory, which has its own). Matching items get a green frame and the rest are dimmed. It finds items by English name, Chinese name or pinyin initials (zs = 钻石), whatever language your game uses; several words must all match.
- **Chest Memory** (on by default; search key not bound): Remembers what was in every chest, barrel, shulker box, hopper, dispenser, dropper and your ender chest the last time you opened it, separately for each world and server. Shulker boxes inside a chest are looked into too. Search for an item and click a result: a green arrow and a frame through walls lead you to that container.
- **Shulker Box Manager** (on by default): Every shulker box in an inventory gets a small colour tag for what it mostly holds (grey = building blocks, cyan = ores and ingots, red = redstone, orange = food, purple = tools and armor, pink = potions and enchanting, white = mixed) and a small picture of its main item. When you use the item search box, boxes that contain a match get an orange frame.
- **Recipe Book Helper** (on by default): Pick a recipe you cannot craft yet. The preview gets green frames on ingredients you have and red frames on missing ones, and a row above the window lists what is missing. Hover over a missing ingredient to see which shulker box you carry or which remembered chest has it; click it to be led to the nearest chest (uses Chest Memory).
- **Hotbar Layouts** (on by default; keys not bound): Save up to five named hotbar layouts (building, fighting, mining ...) and fetch the items from your backpack into place with one click or one key. Items you do not have are skipped; items are matched by type, not by enchantments.

### Combat and PvP awareness

Everything here that concerns other players only considers players you have a clear line of sight to: a player behind a wall never shows up. Invisible players and spectators are ignored, and the game's player-shaped mannequins count as players.

- **Attack Cooldown Bar** (on by default): While your weapon recharges, a bar under the crosshair fills from red to green with the ticks left next to it, and a soft click plays the moment your next hit is at full strength.
- **Shot Direction** (on by default): When an arrow, trident or other projectile hits you, a red arrow at the crosshair points to where it came from for 3 seconds. Only the direction of the shot is shown, not the shooter.
- **Kill Confirmation & Combat Stats** (on by default; reset key not bound): Shows "Kill confirmed" with a sound when something you hit dies within 5 seconds of your last hit, and keeps count of kills, damage dealt, damage taken and deaths. Works on mobs as well as players; the tally appears for a while after each fight.
- **Opponent Gear Panel** (on by default): Look at a player to see what they hold and wear, with enchantments, and whether they are drinking, eating, drawing a bow, loading a crossbow or blocking right now.
- **Approach Alert** (on by default): A sound and a yellow arrow when a player comes within 12 blocks from any side with nothing solid in between, for example sneaking up behind you. The arrow shows where they were at that moment and does not follow them; it is not a radar.
- **Being Watched** (on by default): A red warning and a sound when a player in front of you keeps their crosshair on you for more than 3 seconds.
- **Loot Timer** (on by default): A countdown over dropped items in plain sight until they disappear (items last 5 minutes). Your game is not told how old an item is, so the clock starts when it first sees the item; for items that were already lying there it is an upper limit, shown with "≤".

### Technical and redstone

- **Redstone Diagnostics** (on by default; keys F8, ] and [): Look at a redstone component and press F8. Everything connected to it is scanned as one machine (within 24 blocks by default) and kept up to date:
  - Signal flow: arrows along redstone dust and out of repeaters, comparators and observers, a red mark where a signal runs out and an orange one on a locked repeater.
  - Bottlenecks: hoppers locked by a redstone signal, the total repeater delay, the slowest repeating part, and a warning when more items drop than one hopper line can carry.
  - Overview: how many of each component there are and how many are powered, lit, extended or locked right now.
  - Output rate: dropped items at the machine counted per minute and per hour. For machines that fill a chest, open the chest once after scanning and again later.
  - Slices: ] and [ step through single layers of the machine, which are then shown through the blocks around them.
  - Press F8 while looking at the sky, or while sneaking, to clear everything.
- **Entity Counter** (off by default): Counts the entities your game has loaded, by kind, and lists the most common types. The dropped-items line turns yellow and then red with "LAG RISK" at 100 items (adjustable), and the biggest pile of dropped items is located. Only dropped items get a location, never mobs or players.

### Visuals and world overlays

- **Fullbright** (off by default): See in the dark as if you had Night Vision, without the potion effect or its icon. The brightness is adjustable.
- **Chunk Borders** (off by default): The chunk you stand in gets see-through coloured walls from the bottom to the top of the world, with height lines every 8 blocks near you and marks at the corners of the neighbouring chunks.
- **Slime Chunks** (off by default): Slime chunks around you get a see-through green layer on the ground and corner posts through the whole height of the world, and a line says whether you stand in one. It needs the world seed: in single-player it is read from the world, on a server you type it into the settings once (remembered for each server). Overworld only.

### Grey zone (off by default)

These are fine in single-player and on your own server. They press your own keys for you or show more than the normal view, so many public servers forbid them, and anti-cheat plugins may kick you (for Freecam in particular). They are listed under "Grey zone - careful on public servers" in the settings screen, and their keys only work after you switch them on there.

- **AFK Clicker** (key F7 starts and stops it): Locks your view and clicks or holds the attack or use button for you at a set pace, and stops at once when you get hurt. Presets: mob farm (swing a sword), fishing farm or eating (hold right), cobblestone generator (hold left), fast right click, and trading (one right click per second).
- **Freecam** (key F6): The camera leaves your body and flies freely (movement keys, Jump to rise, Sneak to sink, Sprint for triple speed) while your body stays where it is; clicks do nothing to the world. It shows only what your game has already loaded, lights up dark places while you fly, and ends when you get hurt.
  - **Markers (waypoints):** while flying, right-click to put a numbered pink marker on the block you look at, and left-click a marker to remove it. Back in your body, pink arrows around the crosshair and a beam in the world lead you to each marker; a marker disappears when you reach it. Markers are remembered for each world and server.
- **Elytra One-Key Take-off** (key V): Look up and press one key: it jumps, opens the elytra and fires a rocket from your hotbar or off hand, then switches back to the slot you had selected. Without rockets it only jumps and opens the elytra. It never turns your view for you.
- **Water & Lava Vision**: No fog under water, and inside lava you can make out the blocks around you (12 blocks by default).
- **Rear-view Mirror**: A small live picture of what is directly behind you in a screen corner, flipped left-right like a real mirror by default. The world is drawn twice per frame for it, so expect roughly half your usual frame rate.

### X-ray add-on (separate download)

X-ray is deliberately not part of QoL Bundle. Many servers ban X-ray, and not everyone wants it in their mods folder, so it ships as its own small mod, **QoL Bundle: X-ray add-on**. If you do not want it, simply do not install it: the main mod has no X-ray feature.

When the add-on is installed next to QoL Bundle, the module **X-ray (add-on)** appears in the Grey zone group of the settings screen (off by default, no key). Switched on, it outlines ores within 32 blocks through walls and ground, each ore in its own colour: diamond cyan, gold yellow, redstone red, lapis blue, emerald green, iron light brown, ancient debris brown. Coal, copper and nether quartz can be switched on, and any other block can be added by its id (for example `minecraft:spawner`). It only draws outlines on top of the world, can only see blocks your game has received, and is meant for single-player and your own server only.

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

### Opening the settings

- Press **K** while playing, or
- with [Mod Menu](https://modrinth.com/mod/modmenu) installed: **Mods → QoL Bundle → the settings button**. This also works from the title screen.

The settings screen lists every module under five headings: **Technical**, **Information**, **Tools**, **PvP awareness** and **Grey zone - careful on public servers**. Each row has the module's name and a short description, a **Settings...** button and an **ON/OFF** switch. Hover over the switch to read the full description.

**Settings...** opens the module's own page: the **Enabled** switch at the top, then its options (switches, sliders, buttons that cycle through choices, text boxes). Hover over an option for a longer explanation where there is one. **Reset to defaults** restores that module's options. Hotbar Layouts and Chest Memory also have an **Open** button for their own screen. Changes are saved automatically.

Where each module sits in the settings screen:

| Heading in the settings screen | Modules |
|---|---|
| Technical | Fullbright, Info HUD, Armor Durability HUD, Break Progress, Portal Calculator, Nether Roof Helper, Beacon & Conduit Range, Entity Counter, Chunk Borders, Slime Chunks, Redstone Diagnostics |
| Information | Fall Damage Preview, Bed & Respawn Point Keeper, Villager Trade Peek, Throw Landing Preview, Lava Safety Net, Escape Trail |
| Tools | Durability Alert, Elytra Dashboard, Sound Compass, Chat Enhancements, Multilingual Item Search, Hotbar Layouts, Chest Memory, Shulker Box Manager, Recipe Book Helper, Placement Master |
| PvP awareness | Attack Cooldown Bar, Loot Timer, Shot Direction, Kill Confirmation & Combat Stats, Opponent Gear Panel, Approach Alert, Being Watched |
| Grey zone - careful on public servers | AFK Clicker, Freecam, Elytra One-Key Take-off, Water & Lava Vision, Rear-view Mirror, X-ray (add-on, only when installed) |

### Default keys

| Action (as named in Controls) | Default key | Where to change it |
|---|---|---|
| Open QoL Bundle settings | K | Options → Controls → Key Binds → QoL Bundle |
| Start / stop the AFK clicker | F7 | Options → Controls → Key Binds → QoL Bundle |
| Toggle Freecam | F6 | Options → Controls → Key Binds → QoL Bundle |
| Elytra one-key take-off | V | Options → Controls → Key Binds → QoL Bundle |
| Placement: keep last orientation (hold) | Left Alt | Options → Controls → Key Binds → QoL Bundle |
| Diagnostics: scan the machine you look at / clear | F8 | Options → Controls → Key Binds → QoL Bundle |
| Diagnostics: next layer | ] | Options → Controls → Key Binds → QoL Bundle |
| Diagnostics: previous layer | [ | Options → Controls → Key Binds → QoL Bundle |
| Search chat history | not bound | Options → Controls → Key Binds → QoL Bundle |
| Search remembered chests | not bound | Options → Controls → Key Binds → QoL Bundle |
| Open hotbar layouts | not bound | Options → Controls → Key Binds → QoL Bundle |
| Apply hotbar layout 1 to 5 | not bound | Options → Controls → Key Binds → QoL Bundle |
| Clear Freecam markers | not bound | Options → Controls → Key Binds → QoL Bundle |
| Escape trail: show / hide the way back | not bound | Options → Controls → Key Binds → QoL Bundle |
| Combat stats: reset | not bound | Options → Controls → Key Binds → QoL Bundle |

Apart from K, a module's keys only work while that module is switched on. QoL Bundle has no chat commands.

### Common tasks

**Switch a module on or off**
1. Press K.
2. Find the module (see the table above) and click its ON/OFF button.

**Find an item in your chests (Chest Memory)**
1. Play normally: every container you open is remembered.
2. Press E and click **Chest memory** in the bottom-right corner (or bind "Search remembered chests").
3. Type an item name in English or Chinese, or its pinyin initials. Results show how many, how far away and how long ago you saw them; shulker boxes you carry are listed first.
4. Click a result. A green arrow and a frame lead you there; they go away when you open that container (or after 120 seconds).

**Craft something you lack ingredients for (Recipe Book Helper)**
1. Open the recipe book and click a recipe you cannot craft yet.
2. Red frames mark missing ingredients; the row above the window lists them.
3. Hover over a missing ingredient to see where it is, click it to be led to the nearest chest that has it.

**Save and apply a hotbar layout**
1. Arrange your hotbar the way you like it.
2. Press E, click **Hotbar layouts** (bottom right), give a row a name and click **Save**.
3. Later, click **Apply** on that row, or bind "Apply hotbar layout 1" to 5 to a key. Items are moved one per tick; missing items are skipped and counted in a message.

**Check a redstone machine (Redstone Diagnostics)**
1. Look at any redstone component of the machine and press **F8**. A panel appears in the top-left corner and marks appear on the machine.
2. Press **]** and **[** to look at one layer at a time; past the last layer you are back to the whole machine.
3. For output into a chest: open the output chest once after scanning, then again at least 20 seconds later (Chest Memory must be on).
4. Press **F8** at the sky or while sneaking to clear. If the machine was changed, the panel asks you to scan again.

**Place a row of blocks facing the same way (Placement Master)**
1. Place the first block, for example a stair, the way you want it.
2. Hold **Left Alt** while placing the next ones. A green preview means it will match; when it is red, the click is held back and nothing is placed.

**Use Freecam and markers (grey zone)**
1. Press K, switch on **Freecam** in the Grey zone group.
2. Press **F6** to fly out of your body. Right-click to mark blocks, left-click a marker to remove it.
3. Press **F6** again to return, then follow the pink arrows.

**Show slime chunks on a server**
1. Switch on **Slime Chunks**.
2. In single-player, leave the seed empty. On a server, type the world seed into **World seed**; it is remembered for that server.

**Share your settings**
1. Click **Copy share code** at the bottom of the settings screen. All switches and settings, including saved hotbar layouts, are copied as one line of text starting with `QOL1:`.
2. Your friend copies that line and clicks **Import share code**, then confirms. Things remembered per world (portals, respawn point, chests, Freecam markers) are not part of the code.

**Use the X-ray add-on**
1. Put the add-on jar next to QoL Bundle in your `mods` folder.
2. Press K and switch on **X-ray (add-on)** in the Grey zone group. Choose the ores in its settings.

## Settings

The most useful options. Every module has more; hover over an option in game to read what it does.

| Module | Option (as shown in game) | Default | What it does |
|---|---|---|---|
| Durability Alert | Warn at or below | 5% | Remaining durability that sets off the alert (1 to 50%). |
| Durability Alert | Remind again on every further use | Off | Warn again each time the item loses more durability. |
| Fullbright | Brightness | 100% | How bright dark places become (10 to 100%). |
| Info HUD | Position | Top left | Screen corner of the text. |
| Info HUD | Text size | 100% | 50 to 200%. |
| Info HUD | Real-world clock | Off | Adds the time of your computer's clock. |
| Armor Durability HUD | Show durability as | Uses left | Uses left, Percent or Both. |
| Armor Durability HUD | Show free inventory slots | On | The chest icon row with your empty slots. |
| Portal Calculator | Look distance (blocks) | 24 | How far away a portal is recognised. |
| Nether Roof Helper | Destination (Overworld x, z) | empty | For example `1200, -340`; marked on the heading tape. |
| Entity Counter | Warn at this many dropped items | 100 | The dropped-items line turns yellow at half and red at this number. |
| Chunk Borders | Color / Wall opacity | Yellow / 25% | Look of the walls; 8 colours to choose from. |
| Slime Chunks | World seed | empty | Needed on servers; in single-player leave it empty. |
| Slime Chunks | Range (chunks) | 4 | How many chunks around you are checked (1 to 8). |
| Fall Damage Preview | Also show when the landing is safe | Off | By default only shown when the landing will hurt. |
| Bed & Respawn Point Keeper | Tell whether the bed you look at is yours | On | The hint under the crosshair when you look at a bed. |
| Villager Trade Peek | Most trades to list | 10 | 3 to 16. |
| Lava Safety Net | How far below to look (blocks) | 5 | Depth of the open-lava warning (1 to 12). |
| Elytra Dashboard | Position | Below the crosshair | Or one of the four corners. |
| Elytra Dashboard | Rockets turn yellow at | 8 | Warning colour for the rocket count. |
| Sound Compass | Seconds a sound stays | 3 | 1 to 8. |
| Sound Compass | Ignore your own sounds | On | Hides your own footsteps and the like. |
| Chat Enhancements | Timestamps with seconds | Off | `[13:17:05]` instead of `[13:17]`. |
| Chat Enhancements | Extra words to watch for | empty | Comma-separated words that count as a mention. |
| Chat Enhancements | Keep chat after a reconnect | On | Brings the old chat back when you rejoin. |
| Multilingual Item Search | Match pinyin initials | On | Lets "zs" find 钻石 (diamond). |
| Chest Memory | Mark as possibly outdated after (days) | 7 | Older records are marked "(may be out of date)". |
| Chest Memory | Keep pointing for (seconds) | 120 | How long the arrow to a chest stays. |
| Shulker Box Manager | Search inside boxes | On | Orange frame on boxes that hold what you search for. |
| Recipe Book Helper | Green / red frames on the recipe preview | On | Marks which ingredients you have. |
| Placement Master | Preview which blocks | Only blocks with a direction | Or All blocks. |
| Placement Master | Preview opacity | 55% | 20 to 90%. |
| Placement Master | Orientation lock (hold the key) | On | The Left Alt lock. |
| Redstone Diagnostics | Scan range (blocks) | 24 | How far from the first component the scan reaches (8 to 48). |
| Redstone Diagnostics | Slice direction | Layers by height (Y) | Or slices east-west (X) / north-south (Z). |
| Escape Trail | How much of the way to remember | 30 s | Seconds of walking (10 to 120); standing still does not use it up. |
| Escape Trail | When to show | After taking damage | Or Always. |
| Escape Trail | Stay visible after damage for | 15 s | 5 to 60 s. |
| Attack Cooldown Bar | Sound volume | 30% | Volume of the "ready" click. |
| Loot Timer | Range (blocks) | 16 | 4 to 32. |
| Kill Confirmation & Combat Stats | Always show | Off | Keep the tally on screen all the time. |
| Opponent Gear Panel | Range (blocks) | 32 | 8 to 64. |
| Approach Alert | Alert distance (blocks) | 12 | 4 to 32. |
| Approach Alert | Only sneaking players | Off | Ignore players who are not sneaking. |
| Being Watched | After how long | 3 s | 1 to 10 s. |
| AFK Clicker | Preset | Custom (use the two settings below) | Ready-made setups for common farms. |
| AFK Clicker | Ticks between clicks | 10 | 20 ticks = 1 second (used with the Custom preset). |
| AFK Clicker | Stop when hurt | On | Stops at the first damage. |
| Freecam | Flying speed (blocks per tick) | 0.5 | 0.1 to 3.0; hold Sprint for three times as fast. |
| Freecam | Leave when hurt | On | Returns you to your body when you take damage. |
| Freecam | Most markers at once | 10 | The oldest marker makes room (1 to 30). |
| Water & Lava Vision | How far you see in lava (blocks) | 12 | 4 to 32. |
| Rear-view Mirror | Size (share of the screen width) | 28% | 10 to 50%. |
| Rear-view Mirror | Left-right like a real mirror | On | Off shows the view as if you turned round. |
| X-ray (add-on) | Range (blocks) | 32 | 8 to 64. |
| X-ray (add-on) | Most outlines at once | 400 | When there are more, the nearest are shown (50 to 2000). |
| X-ray (add-on) | Extra blocks | empty | Block ids separated by commas, e.g. `minecraft:spawner,minecraft:chest`. |

Settings are stored in `config/qolbundle.json`. If that file is damaged, the defaults are used and the damaged file is kept as `qolbundle.json.broken`. Things remembered per world (portals seen, respawn point, chest contents, slime chunk seed, Freecam markers) are stored in `config/qolbundle/worlds/`, one file per world or server.

## Requirements

| | Version |
|---|---|
| Minecraft | Java Edition 1.21.11 |
| Fabric Loader | 0.19.5 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | required (built against 0.141.6+1.21.11) |
| Java | 21 or newer |
| [Mod Menu](https://modrinth.com/mod/modmenu) | optional, adds a settings button to the mod list (built against 17.0.1) |
| QoL Bundle: X-ray add-on | optional, separate jar; needs QoL Bundle (use the same version) |

QoL Bundle runs on the client only. The server does not need it, and the mod does not add any network channel of its own.

## Compatibility

- **Sodium, Iris and shader packs:** not fully tested yet. If something looks wrong, these are the most likely places: the see-through block of Placement Master, the Rear-view Mirror, the underground view of Freecam (it works differently when Sodium is installed), and the lines and walls drawn into the world (chunk borders, slime chunks, landing markers, X-ray outlines). Fullbright may have no effect while a shader pack is active.
- **Multiplayer:** so far tested in single-player only. Modules that read server messages or other players (respawn point, chat mentions, portal links, the PvP modules) may behave differently on servers with unusual plugins.
- **Other mods:** a mod with its own Freecam or a utility client should not run its Freecam at the same time as this one. Other chat mods that add timestamps or keep chat history can overlap with Chat Enhancements; switch off the matching option. The item search box sits in the bottom-left corner of the screen so it does not cover buttons other mods put next to the inventory.
- **Keys:** K, F6, F7, F8, V, Left Alt, [ and ] may already be used by other mods. Rebind them under Options → Controls → Key Binds → QoL Bundle.
- **What is sent to the server:** Placement Master never changes what is sent; it only holds back a click. Hotbar Layouts moves items with ordinary inventory clicks, one per tick. The AFK Clicker presses your own attack and use keys; Elytra One-Key Take-off presses your jump key and uses a rocket just like a right click would.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 1.21.11.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11 and put it into your `mods` folder.
3. Download `qolbundle-0.1.0.jar` and put it into the same `mods` folder.
4. Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for a settings button in the mod list.
5. Optional, only if you want X-ray: `qolbundle-xray-addon-0.1.0.jar`.
6. Start the game and press **K** in a world.

## FAQ

**Does the server need QoL Bundle?**
No. It is client-side only and works on servers that do not have it.

**Can I use it on public servers?**
Most modules only show information your game already has, and the PvP modules never look through walls. Server rules differ, though. The grey-zone modules and the X-ray add-on are meant for single-player and your own server; many public servers forbid them. When in doubt, ask the server staff.

**A key does nothing.**
Switch the module on first (press K). Then check Options → Controls → Key Binds for a key used twice.

**My screen is too busy.**
Switch off the modules you do not need. Text from several modules stacks in the screen corners without overlapping; text in the top corners is hidden while the F3 screen is open, and F1 hides everything.

**Portal Calculator says a new portal will be built, but there is one.**
Your game only knows the dimension you are in, so the mod only knows portals you have seen. Go through once and it remembers the other side.

**Villager Trade Peek says "No data yet".**
Open that villager's trading screen once. The server only sends trades when you do.

**Slime Chunks asks for a seed.**
Servers do not send the world seed. Type it into the module's settings once; in single-player it is read automatically.

**Can I copy my settings to another computer or give them to a friend?**
Yes, with **Copy share code** and **Import share code** at the bottom of the settings screen.

## Known limitations

- Version 0.1.0 has been tested in single-player. It has not yet been tested on public servers or in depth with Sodium, Iris or shader packs. The PvP modules have not yet been tried in real fights against other players.
- Placement Master: chests, beds, signs, banners and similar blocks get only the outline and the arrow, no see-through block.
- The Rear-view Mirror roughly halves your frame rate. Freecam also costs some frame rate while flying, because everything around the camera is drawn, even underground.
- Freecam, Entity Counter, Chest Memory and the X-ray add-on only know what your game has loaded or what you have opened. Chunks far from your body stay empty in Freecam.
- Redstone Diagnostics cannot see inside hoppers and chests you have not opened, cannot read a comparator's output strength, and measures very fast clocks (under 4 game ticks) inaccurately. It has mostly been tried on small machines.
- Recipe Book Helper was mainly checked on the 2×2 crafting grid of the inventory; the crafting table and furnace use the same code.
- Kill Confirmation counts a kill when the target dies within 5 seconds of your last hit, so another player's finishing blow can count as yours. "Damage dealt" is wrong on servers that hide other players' health; kills are still counted.
- Chat mentions are found by the usual chat formats ("name: message", "<name> message" and similar); servers with unusual formats may not be recognised.
- The respawn point is worked out from the game's messages. Servers that change how beds work may confuse it.
- Shulker Box Manager's colour tags are a rough guess from item names; some items may land in the wrong group.
- Chat history and villager trades are kept in memory only: chat until you close the game, trades until you leave the world.
- Elytra Dashboard counts flight time as one durability point per second, so with Unbreaking you can fly longer than shown.
- Fall Damage Preview assumes you drop straight down; it does not predict sideways movement.

## Credits

- Made by Autyism.
- Built on [Fabric](https://fabricmc.net/) Loader and Fabric API; optional settings button through [Mod Menu](https://modrinth.com/mod/modmenu) by TerraformersMC.
- The pinyin initials table used by the item search was generated with [pypinyin](https://github.com/mozillazg/python-pinyin) (MIT License).

## License

`GPL-3.0`. QoL Bundle and the X-ray add-on are free software under the GNU General Public License v3.0: you may use, share and change them, and changed versions you share must stay under the same license. See [LICENSE](LICENSE).
