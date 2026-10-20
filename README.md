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

`start`, `add`, `undo`, `finish`, `cancel`, `show`, `hide`, and `edit` without coordinates also need a player in a world, because they use that operator's position or the preview belongs to them. The console cannot run those. `list`, `insert`, `delete`, `edit` with coordinates, `remove`, `rename`, `color`, `help`, and `reload` can be run by any level-2 source, including the console.

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

`add` with no coordinates uses your current x and z. `add <x> <z>` uses those integers. The marker for a new point sits just above the block at your feet. The first point locks the dimension you are in. Later points must be added from that same dimension. A polygon needs at least 3 points.

While you are creating a polygon, corners and edges are shown with block displays, including the edge that closes back to the first point. The number at each corner is that point's index, starting at 1. The first corner is lime, the latest corner is orange, and the corners between them are white. Nearby players can see these displays. They are not saved with the world.

```
/arowsmod map polygon undo
```

`undo` removes the last point and updates the displays. If that was the only point, the in-progress polygon is cancelled.

`finish <name>` saves the polygon under that name for the player you started it for and removes the in-progress displays. The name is one word. That player cannot already have a polygon with the same name, ignoring case. In-progress polygons do not count toward the per-player cap.

```
/arowsmod map polygon cancel
```

`cancel` drops your in-progress polygon and its displays. It does not remove a finished one.

Once Steve already has a color, start the next polygon without one:

```
/arowsmod map polygon start Steve
```

Passing a color on `start` after a color exists is rejected. Change it with `color` instead.

### After a polygon is saved

```
/arowsmod map polygon list Steve
/arowsmod map polygon list Steve spawn
/arowsmod map polygon color Steve 2266aa
/arowsmod map polygon rename Steve spawn hub
/arowsmod map polygon remove Steve hub
/arowsmod map polygon show Steve spawn
/arowsmod map polygon hide
/arowsmod map polygon insert Steve spawn 2 100 200
/arowsmod map polygon edit Steve spawn 2 120 200
/arowsmod map polygon edit Steve spawn 2
/arowsmod map polygon delete Steve spawn 2
```

`list <player>` prints that player's finished polygons. `list <player> <name>` prints each saved point as `1: x, z`. `color` stores the new color and restyles every finished polygon for that player. `rename` matches the old name without regard to case. The new name must not already belong to a different polygon for that player. The rename is saved only after Pl3xMap replaces the marker. `remove` deletes the saved polygon and its marker. `remove` and `rename` also match the existing name without regard to case.

`show` draws the same corner and edge displays for a finished polygon. Each marker uses the top solid block at that x,z, plus one. A point whose chunk is not loaded is skipped, and the command says which indexes were skipped. `hide` removes the preview you are showing. A preview also goes away when you disconnect, when the server stops, when you show a different polygon, and after 3 minutes. These displays are not written into the world.

Point indexes start at 1. `insert` puts a new point at that index. An index one past the last point appends. `edit` moves a point. Without x and z, it uses your current position. `delete` removes a point and refuses to leave fewer than 3. The file is updated only after Pl3xMap accepts the new marker. If that polygon is currently shown, its displays are refreshed. Y is not stored in `polygons.json`.

## Saved polygons

Finished polygons and player colors are stored in `config/arowsmod/polygons.json`. Only x and z are stored. In-progress polygons, their feet height, and the in-world previews are kept in memory only.

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
