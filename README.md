# ArowsMod

ArowsMod is a Fabric server companion to [Pl3xMap](https://github.com/granny/Pl3xMap) for Minecraft 26.3. Operators draw polygons on the live map for a chosen player. Each player has one color. The mod id is `arowsmod`. The display name is ArowsMod. The author is Arowkey.

## Requirements

- Minecraft 26.3
- Java 25
- Fabric Loader 0.19.5 or newer
- Fabric API
- Pl3xMap 26.3-557 or newer

Put both ArowsMod and Pl3xMap in the server `mods` folder. ArowsMod does not draw polygons on a client that is not running the dedicated server with Pl3xMap.

## Install

1. Copy `arowsmod-*.jar` into the server `mods` folder, next to the Pl3xMap jar.
2. Start the server once.

The first start creates `config/arowsmod.json` and, after the first saved polygon or color, `config/arowsmod/polygons.json`.

## Who can use the commands

Every ArowsMod command requires operator permission (permission level 2).

`start`, `add`, `finish`, and `cancel` also need a player in a world, because they use that operator's position and dimension. The console cannot run those four. `list`, `remove`, `rename`, `color`, `help`, and `reload` can be run by any level-2 source, including the console.

`/arowsmod map polygon help`, or `/arowsmod map polygon` with nothing after it, prints a short reminder in chat and links to this page.

## Polygon creation

Colors are six hex digits with no `#`, for example `3dd68c`. The first polygon for a player must include a color. Later polygons for that player reuse the saved color.

Stand in the world where the polygon belongs, then:

```
/arowsmod map polygon start Steve 3dd68c
/arowsmod map polygon add
/arowsmod map polygon add 100 200
/arowsmod map polygon finish spawn
```

`add` with no coordinates uses your current x and z. `add <x> <z>` uses those integers. The first point locks the dimension you are in. Later points must be added from that same dimension. A polygon needs at least 3 points.

`finish <name>` saves the polygon under that name for the player you started it for. The name is one word. That player cannot already have a polygon with the same name, ignoring case. In-progress polygons do not count toward the per-player cap.

```
/arowsmod map polygon cancel
```

`cancel` drops your in-progress polygon. It does not remove a finished one.

Once Steve already has a color, start the next polygon without one:

```
/arowsmod map polygon start Steve
```

Passing a color on `start` after a color exists is rejected. Change it with `color` instead.

### After a polygon is saved

```
/arowsmod map polygon list Steve
/arowsmod map polygon color Steve 2266aa
/arowsmod map polygon rename Steve spawn hub
/arowsmod map polygon remove Steve hub
```

`list` prints that player's finished polygons. `color` stores the new color and restyles every finished polygon for that player. `rename` matches the old name without regard to case. The new name must not already belong to a different polygon for that player. The rename is saved only after Pl3xMap replaces the marker. `remove` deletes the saved polygon and its marker. `remove` and `rename` also match the existing name without regard to case.

## Saved polygons

Finished polygons and player colors are stored in `config/arowsmod/polygons.json`. In-progress polygons are kept in memory only and are not written to that file.

There is no global default color in the config. A player's color is set by `start` or `color` and stored with their polygons.

## Config

`config/arowsmod.json` controls the map layer and how polygons are drawn. Lines starting with `//` are comments. The loader ignores them, leaves them in the file, and does not apply those options.

`/arowsmod reload` reads the file again, restyles every saved polygon, and updates the label on the existing map layer. It does not rewrite the file. A missing or invalid file leaves the current settings and markers unchanged. Lowering `max-polygons-per-player` does not delete polygons that are already saved.

An existing config file is not replaced with a new template. Keys added in a later version are not filled in for you. Add them by hand, or delete `config/arowsmod.json` and start the server so ArowsMod can create it again.

| Key | Default | Meaning |
| --- | --- | --- |
| `layer-name` | `ArowsMod` | Name players see on the map layer |
| `max-polygons-per-player` | `10` | Most finished polygons one player can have |
| `stroke-enabled` | `true` | Draw the outline |
| `stroke-weight` | `3` | Outline thickness |
| `stroke-opacity` | `1.0` | Outline opacity, from 0 to 1 |
| `stroke-line-cap` | `round` | `butt`, `round`, or `square` |
| `stroke-line-join` | `round` | `miter`, `round`, or `bevel` |
| `fill-enabled` | `true` | Fill the polygon |
| `fill-opacity` | `0.35` | Fill opacity, from 0 to 1 |
| `fill-type` | `evenodd` | `evenodd` or `nonzero` |

Stroke and fill both use the player's color. `stroke-color` and `fill-color` are comments in the default file and are not separate colors.

The generated file also includes tooltip and popup settings (pane, offset, tooltip direction, popup size, and close behavior). Commented options such as `tooltip-content`, `popup-content`, dash pattern, and marker pane stay inactive until you remove the `//`. If you set `tooltip-content` or `popup-content`, Pl3xMap renders that value as HTML. The built-in pane name `tooltipPane` is not sent to the map, because that hides the tooltip. A popup is added only when `popup-content` is set.

## For server admins

- The Pl3xMap layer id stays `arowsmod`. Changing `layer-name` changes the label only, so existing markers stay on the same layer.
- Finished polygons are registered again when the server starts and Pl3xMap has loaded that world.
- A bad `config/arowsmod.json` on reload does not replace the settings already in memory and does not restyle markers.
- A bad `config/arowsmod/polygons.json` does not get overwritten with an empty file, and it does not clear polygons that were already loaded.
- The default tooltip is the polygon name and the player name, with HTML characters escaped. Tooltip or popup HTML that you write in the config is rendered as HTML.
- Only operators can run these commands. Do not grant them through a lower permission.

## License

ArowsMod is licensed under LGPL-3.0.
