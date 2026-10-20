package com.arowkey.arowsmod.command;

import com.arowkey.arowsmod.ArowsMod;
import com.arowkey.arowsmod.map.ArowsConfig;
import com.arowkey.arowsmod.map.MapPoint;
import com.arowkey.arowsmod.map.DraftPoint;
import com.arowkey.arowsmod.map.Pl3xMapBridge;
import com.arowkey.arowsmod.map.PlayerColor;
import com.arowkey.arowsmod.map.PolygonDraft;
import com.arowkey.arowsmod.map.PolygonStore;
import com.arowkey.arowsmod.map.PolygonVisuals;
import com.arowkey.arowsmod.map.SavedPolygon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.pl3x.map.core.markers.Point;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MapPolygonCommands {
	private MapPolygonCommands() {
	}

	public static void register(ArowsConfig config, PolygonStore store, Pl3xMapBridge bridge) {
		PolygonVisuals visuals = new PolygonVisuals();
		visuals.start();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				register(dispatcher, registryAccess, config, store, bridge, visuals));
	}

	/**
	 * Every polygon command and {@code /arowsmod reload} require operator permission
	 * ({@link Permissions#COMMANDS_GAMEMASTER}, level 2). Start, add-point, undo-point, finish, cancel,
	 * show without a Y, and insert-point or edit-point without coordinates need a player position.
	 * Show with a Y, hide, and the other polygon commands can run from the console.
	 * undo-point removes the last in-progress point and cancels the draft when none remain.
	 * There are no aliases for the previous command names.
	 */

	private static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess,
			ArowsConfig config,
			PolygonStore store,
			Pl3xMapBridge bridge,
			PolygonVisuals visuals
	) {
		dispatcher.register(Commands.literal("arowsmod")
				.then(Commands.literal("reload")
						.requires(MapPolygonCommands::isOperator)
						.executes(context -> reload(context.getSource(), config, store, bridge)))
				.then(Commands.literal("map")
						.then(Commands.literal("polygon")
								.requires(MapPolygonCommands::isOperator)
								.executes(context -> help(context.getSource()))
								.then(Commands.literal("help")
										.executes(context -> help(context.getSource())))
								.then(Commands.literal("start")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.executes(context -> start(context.getSource(), store, profiles(context), null))
												.then(Commands.argument("color", com.mojang.brigadier.arguments.StringArgumentType.word())
														.executes(context -> start(
																context.getSource(),
																store,
																profiles(context),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "color")
														)))))
								.then(Commands.literal("color")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("color", com.mojang.brigadier.arguments.StringArgumentType.word())
														.executes(context -> color(
																context.getSource(),
																store,
																bridge,
																profiles(context),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "color")
														)))))
								.then(Commands.literal("add-point")
										.executes(context -> addHere(context.getSource(), store, visuals))
										.then(Commands.argument("x", IntegerArgumentType.integer())
												.then(Commands.argument("z", IntegerArgumentType.integer())
														.executes(context -> addAt(
																context.getSource(),
																store,
																visuals,
																IntegerArgumentType.getInteger(context, "x"),
																IntegerArgumentType.getInteger(context, "z")
														)))))
								.then(Commands.literal("undo-point")
										.executes(context -> undo(context.getSource(), store, visuals)))
								.then(Commands.literal("finish")
										.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
												.executes(context -> finish(
														context.getSource(),
														config,
														store,
														bridge,
														visuals,
														com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")
												))))
								.then(Commands.literal("cancel")
										.executes(context -> cancel(context.getSource(), store, visuals)))
								.then(Commands.literal("show")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.executes(context -> show(
																context.getSource(),
																store,
																visuals,
																profiles(context),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																null
														))
														.then(Commands.argument("y", IntegerArgumentType.integer())
																.executes(context -> show(
																		context.getSource(),
																		store,
																		visuals,
																		profiles(context),
																		com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																		IntegerArgumentType.getInteger(context, "y")
																))))))
								.then(Commands.literal("hide")
										.executes(context -> hide(context.getSource(), visuals)))
								.then(Commands.literal("list")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.executes(context -> list(context.getSource(), store, profiles(context)))
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.executes(context -> listPoints(
																context.getSource(),
																store,
																profiles(context),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")
														)))))
								.then(Commands.literal("insert-point")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.then(Commands.argument("index", IntegerArgumentType.integer(1))
																.executes(context -> insertHere(
																		context.getSource(),
																		store,
																		bridge,
																		visuals,
																		profiles(context),
																		com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																		IntegerArgumentType.getInteger(context, "index")
																))
																.then(Commands.argument("x", IntegerArgumentType.integer())
																		.then(Commands.argument("z", IntegerArgumentType.integer())
																				.executes(context -> insert(
																						context.getSource(),
																						store,
																						bridge,
																						visuals,
																						profiles(context),
																						com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																						IntegerArgumentType.getInteger(context, "index"),
																						IntegerArgumentType.getInteger(context, "x"),
																						IntegerArgumentType.getInteger(context, "z")
																				))))))))
								.then(Commands.literal("edit-point")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.then(Commands.argument("index", IntegerArgumentType.integer(1))
																.executes(context -> editHere(
																		context.getSource(),
																		store,
																		bridge,
																		visuals,
																		profiles(context),
																		com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																		IntegerArgumentType.getInteger(context, "index")
																))
																.then(Commands.argument("x", IntegerArgumentType.integer())
																		.then(Commands.argument("z", IntegerArgumentType.integer())
																				.executes(context -> edit(
																						context.getSource(),
																						store,
																						bridge,
																						visuals,
																						profiles(context),
																						com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																						IntegerArgumentType.getInteger(context, "index"),
																						IntegerArgumentType.getInteger(context, "x"),
																						IntegerArgumentType.getInteger(context, "z")
																				))))))))
								.then(Commands.literal("remove-point")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.then(Commands.argument("index", IntegerArgumentType.integer(1))
																.executes(context -> delete(
																		context.getSource(),
																		store,
																		bridge,
																		visuals,
																		profiles(context),
																		com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																		IntegerArgumentType.getInteger(context, "index")
																))))))
								.then(Commands.literal("remove")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.executes(context -> remove(
																context.getSource(),
																store,
																bridge,
																profiles(context),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																visuals
														)))))
								.then(Commands.literal("rename")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.then(Commands.argument("new-name", com.mojang.brigadier.arguments.StringArgumentType.word())
																.executes(context -> rename(
																		context.getSource(),
																		store,
																		bridge,
																		profiles(context),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "new-name"),
																visuals
																)))))))));
	}

	private static int help(CommandSourceStack source) {
		Component link = Component.literal("https://github.com/Arowkey/arows-mod").withStyle(style -> style
				.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/Arowkey/arows-mod")))
				.withUnderlined(true));
		Component message = Component.literal("Operators draw Pl3xMap polygons for a player.\n"
				+ "First polygon: /arowsmod map polygon start <player> <RRGGBB>, then add-point, then finish <name>.\n"
				+ "undo-point removes the last in-progress point. With no points left, the draft is cancelled. cancel drops the one in progress.\n"
				+ "/arowsmod reload rereads the config.\n"
				+ "show <player> <name> previews a saved polygon at your Y. show <player> <name> <y> uses that height. The console has to pass <y>.\n"
				+ "Later: list, list <player> <name>, color, rename, remove, insert-point, edit-point, and remove-point.\n"
				+ "insert-point and edit-point use your x and z when coordinates are left out. The console has to pass <x> <z>.\n"
				+ "Colors are six hex digits with no #.\n"
				+ "More detail: ").append(link);
		source.sendSuccess(() -> message, false);
		return 1;
	}

	private static boolean isOperator(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static int reload(CommandSourceStack source, ArowsConfig config, PolygonStore store, Pl3xMapBridge bridge) {
		String failure = config.reload();
		if (failure != null) {
			ArowsMod.warn("Kept the current config. {}", failure);
			source.sendFailure(Component.literal(failure));
			return 0;
		}
		List<SavedPolygon> polygons = store.all();
		Set<String> waitingWorlds = new LinkedHashSet<>();
		int restyled = 0;
		try {
			bridge.refreshLayerLabels();
			for (SavedPolygon polygon : polygons) {
				if (bridge.show(polygon)) {
					restyled++;
				} else {
					waitingWorlds.add(polygon.worldId());
				}
			}
		} catch (RuntimeException exception) {
			ArowsMod.warn("Could not restyle markers after config reload.", exception);
			source.sendFailure(Component.literal("Reloaded the config, but Pl3xMap could not restyle the markers: " + exception.getMessage()));
			return 0;
		}
		int pending = polygons.size() - restyled;
		int restyledCount = restyled;
		int pendingCount = pending;
		ArowsMod.info("{} reloaded config. Restyled {} {} on layer {}.",
				source.getTextName(),
				restyledCount,
				restyledCount == 1 ? "polygon" : "polygons",
				config.layerName());
		for (String worldId : waitingWorlds) {
			ArowsMod.warn("World {} is not on Pl3xMap yet.", worldId);
		}
		source.sendSuccess(() -> Component.literal("Reloaded config. Restyled " + restyledCount + " polygon" + (restyledCount == 1 ? "" : "s") + (pendingCount == 0 ? "." : "; " + pendingCount + " will update when their world loads on Pl3xMap.")), false);
		return 1;
	}

	private static Collection<NameAndId> profiles(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return GameProfileArgument.getGameProfiles(context, "player");
	}

	private static int start(CommandSourceStack source, PolygonStore store, Collection<NameAndId> profiles, String requestedColor) {
		ServerPlayer operator = operator(source);
		if (operator == null) {
			return 0;
		}
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		if (store.draftFor(operator.getUUID()) != null) {
			source.sendFailure(Component.literal("You already have an in-progress polygon. Cancel it before starting another."));
			return 0;
		}
		String existing = store.colorOf(owner.id()).orElse(null);
		String color;
		if (existing == null) {
			color = PlayerColor.parse(requestedColor);
			if (color == null) {
				source.sendFailure(Component.literal("First polygon for " + owner.name() + " needs a color, like 3dd68c."));
				return 0;
			}
			store.rememberColor(owner.id(), color);
		} else if (requestedColor != null) {
			source.sendFailure(Component.literal(owner.name() + " already uses " + existing + ". Change it with /arowsmod map polygon color."));
			return 0;
		} else {
			color = existing;
		}
		String worldId = source.getLevel().dimension().identifier().toString();
		store.putDraft(operator.getUUID(), new PolygonDraft(owner.id(), owner.name(), worldId, color));
		String chosen = color;
		source.sendSuccess(() -> Component.literal("Started a polygon for " + owner.name() + " (" + chosen + ") in " + worldId + "."), false);
		return 1;
	}

	private static int color(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, Collection<NameAndId> profiles, String requestedColor) {
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		String color = PlayerColor.parse(requestedColor);
		if (color == null) {
			source.sendFailure(Component.literal("Color must look like 3dd68c."));
			return 0;
		}
		List<SavedPolygon> polygons = store.recolor(owner.id(), color);
		Set<String> waitingWorlds = new LinkedHashSet<>();
		int restyled = 0;
		try {
			for (SavedPolygon polygon : polygons) {
				if (bridge.show(polygon)) {
					restyled++;
				} else {
					waitingWorlds.add(polygon.worldId());
				}
			}
		} catch (RuntimeException exception) {
			ArowsMod.warn("Could not restyle markers for {}.", owner.name(), exception);
			logColorChanged(source, owner, polygons, 0);
			source.sendFailure(Component.literal("Saved " + color + " for " + owner.name() + ", but Pl3xMap could not restyle the markers: " + exception.getMessage()));
			return 0;
		}
		logColorChanged(source, owner, polygons, restyled);
		for (String worldId : waitingWorlds) {
			ArowsMod.warn("World {} is not on Pl3xMap yet.", worldId);
		}
		int pending = polygons.size() - restyled;
		int restyledCount = restyled;
		int pendingCount = pending;
		if (pendingCount > 0) {
			source.sendSuccess(() -> Component.literal("Set " + owner.name() + " to " + color + ". Restyled " + restyledCount + " polygon" + (restyledCount == 1 ? "" : "s") + "; " + pendingCount + " will update when their world loads on Pl3xMap."), false);
		} else {
			source.sendSuccess(() -> Component.literal("Set " + owner.name() + " to " + color + " and restyled " + restyledCount + " polygon" + (restyledCount == 1 ? "" : "s") + "."), false);
		}
		return 1;
	}

	private static int addHere(CommandSourceStack source, PolygonStore store, PolygonVisuals visuals) {
		ServerPlayer operator = operator(source);
		if (operator == null) {
			return 0;
		}
		Point point = Point.of(source.getPosition().x(), source.getPosition().z());
		return add(source, store, visuals, new MapPoint(point.x(), point.z()));
	}

	private static int addAt(CommandSourceStack source, PolygonStore store, PolygonVisuals visuals, int x, int z) {
		if (operator(source) == null) {
			return 0;
		}
		return add(source, store, visuals, new MapPoint(x, z));
	}

	private static int add(CommandSourceStack source, PolygonStore store, PolygonVisuals visuals, MapPoint point) {
		ServerPlayer operator = source.getPlayer();
		PolygonDraft draft = store.draftFor(operator.getUUID());
		if (draft == null) {
			source.sendFailure(Component.literal("Start a polygon before using add-point."));
			return 0;
		}
		String worldId = source.getLevel().dimension().identifier().toString();
		if (!draft.worldId().equals(worldId)) {
			source.sendFailure(Component.literal("This polygon is in " + draft.worldId() + ". Stay in that world to use add-point."));
			return 0;
		}
		draft.add(new DraftPoint(point.x(), operator.blockPosition().getY(), point.z()));
		visuals.showDraft(operator, draft);
		source.sendSuccess(() -> Component.literal("Added " + point.x() + ", " + point.z() + " (" + draft.size() + " points)."), false);
		return 1;
	}

	private static int undo(CommandSourceStack source, PolygonStore store, PolygonVisuals visuals) {
		ServerPlayer operator = operator(source);
		if (operator == null) {
			return 0;
		}
		PolygonDraft draft = store.draftFor(operator.getUUID());
		if (draft == null || draft.size() == 0) {
			source.sendFailure(Component.literal("You do not have a point for undo-point."));
			return 0;
		}
		draft.removeLast();
		if (draft.size() == 0) {
			store.removeDraft(operator.getUUID());
			visuals.clearDraft(operator.getUUID());
			source.sendSuccess(() -> Component.literal("Removed the last point and cancelled the in-progress polygon."), false);
			return 1;
		}
		visuals.showDraft(operator, draft);
		source.sendSuccess(() -> Component.literal("Removed the last point (" + draft.size() + " points left)."), false);
		return 1;
	}

	private static int finish(CommandSourceStack source, ArowsConfig config, PolygonStore store, Pl3xMapBridge bridge, PolygonVisuals visuals, String name) {
		ServerPlayer operator = operator(source);
		if (operator == null) {
			return 0;
		}
		PolygonDraft draft = store.draftFor(operator.getUUID());
		if (draft == null) {
			source.sendFailure(Component.literal("You do not have an in-progress polygon."));
			return 0;
		}
		if (draft.size() < 3) {
			source.sendFailure(Component.literal("A polygon needs at least 3 points. This one has " + draft.size() + "."));
			return 0;
		}
		if (store.nameTaken(draft.ownerId(), name)) {
			source.sendFailure(Component.literal(draft.ownerName() + " already has a polygon named " + name + "."));
			return 0;
		}
		int max = config.maxPolygonsPerPlayer();
		if (store.countFinished(draft.ownerId()) >= max) {
			source.sendFailure(Component.literal(draft.ownerName() + " already has " + max + " polygons."));
			return 0;
		}
		SavedPolygon polygon = new SavedPolygon(draft.ownerId(), draft.ownerName(), name, draft.worldId(), draft.color(), draft.mapPoints());
		boolean shown;
		try {
			shown = bridge.show(polygon);
		} catch (RuntimeException exception) {
			ArowsMod.warn("Could not register marker {} for {} in {}.", name, draft.ownerName(), draft.worldId(), exception);
			source.sendFailure(Component.literal("Pl3xMap could not register this polygon: " + exception.getMessage()));
			return 0;
		}
		try {
			store.add(polygon);
		} catch (RuntimeException exception) {
			try {
				bridge.hide(polygon);
			} catch (RuntimeException rollback) {
				exception.addSuppressed(rollback);
			}
			ArowsMod.warn("Could not save polygons.json after creating {} for {}.", name, draft.ownerName(), exception);
			source.sendFailure(Component.literal("Could not save this polygon: " + exception.getMessage()));
			return 0;
		}
		store.removeDraft(operator.getUUID());
		visuals.clearDraft(operator.getUUID());
		ArowsMod.info("{} created polygon {} for {} in {} ({} points).",
				source.getTextName(), name, draft.ownerName(), draft.worldId(), polygon.points().size());
		if (shown) {
			source.sendSuccess(() -> Component.literal("Created " + name + " for " + draft.ownerName() + " on the ArowsMod map layer."), false);
		} else {
			source.sendSuccess(() -> Component.literal("Saved " + name + " for " + draft.ownerName() + ". Pl3xMap is not showing " + draft.worldId() + " yet; the marker will be registered when that world loads."), false);
			ArowsMod.warn("World {} is not on Pl3xMap yet.", draft.worldId());
		}
		return 1;
	}

	private static int cancel(CommandSourceStack source, PolygonStore store, PolygonVisuals visuals) {
		ServerPlayer operator = operator(source);
		if (operator == null) {
			return 0;
		}
		PolygonDraft draft = store.removeDraft(operator.getUUID());
		if (draft == null) {
			source.sendFailure(Component.literal("You do not have an in-progress polygon."));
			return 0;
		}
		visuals.clearDraft(operator.getUUID());
		source.sendSuccess(() -> Component.literal("Cancelled the in-progress polygon for " + draft.ownerName() + "."), false);
		return 1;
	}

	private static int list(CommandSourceStack source, PolygonStore store, Collection<NameAndId> profiles) {
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		List<SavedPolygon> owned = store.byOwner(owner.id());
		if (owned.isEmpty()) {
			source.sendSuccess(() -> Component.literal(owner.name() + " has no polygons."), false);
			return 1;
		}
		StringBuilder message = new StringBuilder(owner.name()).append(" has ").append(owned.size()).append(" polygon");
		if (owned.size() != 1) {
			message.append('s');
		}
		message.append(':');
		for (SavedPolygon polygon : owned) {
			message.append("\n- ").append(polygon.name()).append(" (").append(polygon.points().size()).append(" points, ").append(polygon.worldId()).append(')');
		}
		source.sendSuccess(() -> Component.literal(message.toString()), false);
		return owned.size();
	}

	private static int listPoints(CommandSourceStack source, PolygonStore store, Collection<NameAndId> profiles, String name) {
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		SavedPolygon polygon = store.find(owner.id(), name).orElse(null);
		if (polygon == null) {
			source.sendFailure(Component.literal(owner.name() + " has no polygon named " + name + "."));
			return 0;
		}
		StringBuilder message = new StringBuilder(polygon.name());
		int index = 1;
		for (MapPoint point : polygon.points()) {
			message.append('\n').append(index).append(": ").append(point.x()).append(", ").append(point.z());
			index++;
		}
		source.sendSuccess(() -> Component.literal(message.toString()), false);
		return polygon.points().size();
	}

	private static int show(CommandSourceStack source, PolygonStore store, PolygonVisuals visuals, Collection<NameAndId> profiles, String name, Integer y) {
		ServerPlayer operator = source.getPlayer();
		if (operator == null && y == null) {
			source.sendFailure(Component.literal("The console has to pass a Y: /arowsmod map polygon show <player> <name> <y>."));
			return 0;
		}
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		SavedPolygon polygon = store.find(owner.id(), name).orElse(null);
		if (polygon == null) {
			source.sendFailure(Component.literal(owner.name() + " has no polygon named " + name + "."));
			return 0;
		}
		int height = y == null ? operator.blockPosition().getY() : y;
		UUID viewer = operator == null ? PolygonVisuals.CONSOLE_VIEWER : operator.getUUID();
		List<Integer> skipped = visuals.showSaved(source.getServer(), viewer, polygon, height);
		if (skipped == null) {
			source.sendFailure(Component.literal("Pl3xMap world " + polygon.worldId() + " is not loaded."));
			return 0;
		}
		if (skipped.size() == polygon.points().size()) {
			source.sendFailure(Component.literal("No points of " + polygon.name() + " are in a loaded chunk."));
			return 0;
		}
		StringBuilder message = new StringBuilder("Showing ").append(polygon.name()).append(" for ").append(owner.name()).append(" at Y ").append(height).append(". It hides after 3 minutes.");
		if (!skipped.isEmpty()) {
			message.append(" Skipped points ");
			for (int index = 0; index < skipped.size(); index++) {
				if (index > 0) {
					message.append(", ");
				}
				message.append(skipped.get(index));
			}
			message.append(" because those chunks are not loaded.");
		}
		source.sendSuccess(() -> Component.literal(message.toString()), false);
		return 1;
	}

	private static int hide(CommandSourceStack source, PolygonVisuals visuals) {
		ServerPlayer operator = source.getPlayer();
		visuals.clearShow(operator == null ? PolygonVisuals.CONSOLE_VIEWER : operator.getUUID());
		source.sendSuccess(() -> Component.literal("Hid the polygon preview."), false);
		return 1;
	}

	private static int insertHere(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, PolygonVisuals visuals, Collection<NameAndId> profiles, String name, int index) {
		MapPoint point = here(source);
		if (point == null) {
			return 0;
		}
		return insert(source, store, bridge, visuals, profiles, name, index, point.x(), point.z());
	}

	private static int insert(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, PolygonVisuals visuals, Collection<NameAndId> profiles, String name, int index, int x, int z) {
		return changePoints(source, store, bridge, visuals, profiles, name, index, "inserted", points -> {
			if (index > points.size() + 1) {
				return "Point index must be from 1 to " + (points.size() + 1) + ".";
			}
			points.add(index - 1, new MapPoint(x, z));
			return null;
		});
	}

	private static int editHere(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, PolygonVisuals visuals, Collection<NameAndId> profiles, String name, int index) {
		MapPoint point = here(source);
		if (point == null) {
			return 0;
		}
		return edit(source, store, bridge, visuals, profiles, name, index, point.x(), point.z());
	}

	private static MapPoint here(CommandSourceStack source) {
		if (source.getPlayer() == null) {
			source.sendFailure(Component.literal("The console has no position, so it must pass <x> <z>."));
			return null;
		}
		Point point = Point.of(source.getPosition().x(), source.getPosition().z());
		return new MapPoint(point.x(), point.z());
	}

	private static int edit(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, PolygonVisuals visuals, Collection<NameAndId> profiles, String name, int index, int x, int z) {
		return changePoints(source, store, bridge, visuals, profiles, name, index, "moved", points -> {
			if (index > points.size()) {
				return "Point index must be from 1 to " + points.size() + ".";
			}
			points.set(index - 1, new MapPoint(x, z));
			return null;
		});
	}

	private static int delete(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, PolygonVisuals visuals, Collection<NameAndId> profiles, String name, int index) {
		return changePoints(source, store, bridge, visuals, profiles, name, index, "deleted", points -> {
			if (index > points.size()) {
				return "Point index must be from 1 to " + points.size() + ".";
			}
			if (points.size() <= 3) {
				return "A polygon needs at least 3 points.";
			}
			points.remove(index - 1);
			return null;
		});
	}

	private static int changePoints(
			CommandSourceStack source,
			PolygonStore store,
			Pl3xMapBridge bridge,
			PolygonVisuals visuals,
			Collection<NameAndId> profiles,
			String name,
			int index,
			String action,
			PointChange change
	) {
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		SavedPolygon polygon = store.find(owner.id(), name).orElse(null);
		if (polygon == null) {
			source.sendFailure(Component.literal(owner.name() + " has no polygon named " + name + "."));
			return 0;
		}
		List<MapPoint> points = new ArrayList<>(polygon.points());
		String rejected = change.apply(points);
		if (rejected != null) {
			source.sendFailure(Component.literal(rejected));
			return 0;
		}
		SavedPolygon updated = new SavedPolygon(polygon.ownerId(), polygon.ownerName(), polygon.name(), polygon.worldId(), polygon.color(), List.copyOf(points));
		try {
			if (!bridge.show(updated)) {
				ArowsMod.warn("World {} is not on Pl3xMap yet.", polygon.worldId());
				source.sendFailure(Component.literal("Pl3xMap is not showing " + polygon.worldId() + " yet, so this point was not saved."));
				return 0;
			}
		} catch (RuntimeException exception) {
			try {
				bridge.show(polygon);
			} catch (RuntimeException rollback) {
				exception.addSuppressed(rollback);
			}
			ArowsMod.warn("Could not update marker {} for {} in {}.", polygon.name(), owner.name(), polygon.worldId(), exception);
			source.sendFailure(Component.literal("Pl3xMap could not update this polygon: " + exception.getMessage()));
			return 0;
		}
		try {
			store.replace(polygon, updated);
		} catch (RuntimeException exception) {
			try {
				bridge.show(polygon);
			} catch (RuntimeException rollback) {
				exception.addSuppressed(rollback);
			}
			ArowsMod.warn("Could not save polygons.json after editing {} for {}.", polygon.name(), owner.name(), exception);
			source.sendFailure(Component.literal("Could not save this polygon: " + exception.getMessage()));
			return 0;
		}
		visuals.refresh(updated, source.getServer());
		ArowsMod.info("{} {} point {} on polygon {} for {} in {} ({} points).",
				source.getTextName(), action, index, polygon.name(), owner.name(), polygon.worldId(), updated.points().size());
		source.sendSuccess(() -> Component.literal("Updated " + polygon.name() + " for " + owner.name() + " (" + updated.points().size() + " points)."), false);
		return 1;
	}

	@FunctionalInterface
	private interface PointChange {
		String apply(List<MapPoint> points);
	}

	/** The file is updated only after the map marker is replaced. A failed save puts the old marker back. */
	private static int rename(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, Collection<NameAndId> profiles, String name, String newName, PolygonVisuals visuals) {
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		SavedPolygon polygon = store.find(owner.id(), name).orElse(null);
		if (polygon == null) {
			source.sendFailure(Component.literal(owner.name() + " has no polygon named " + name + "."));
			return 0;
		}
		SavedPolygon taken = store.find(owner.id(), newName).orElse(null);
		if (taken != null && taken != polygon) {
			source.sendFailure(Component.literal(owner.name() + " already has a polygon named " + newName + "."));
			return 0;
		}
		if (polygon.name().equals(newName)) {
			source.sendSuccess(() -> Component.literal(polygon.name() + " is already named that."), false);
			return 1;
		}
		SavedPolygon renamed = new SavedPolygon(polygon.ownerId(), polygon.ownerName(), newName, polygon.worldId(), polygon.color(), polygon.points());
		try {
			bridge.renameMarker(polygon, renamed);
		} catch (RuntimeException exception) {
			ArowsMod.warn("Could not rename marker {} for {} in {}.", polygon.name(), owner.name(), polygon.worldId(), exception);
			source.sendFailure(Component.literal("Pl3xMap could not rename this polygon: " + exception.getMessage()));
			return 0;
		}
		try {
			store.replace(polygon, renamed);
		} catch (RuntimeException exception) {
			try {
				bridge.renameMarker(renamed, polygon);
			} catch (RuntimeException rollback) {
				exception.addSuppressed(rollback);
			}
			ArowsMod.warn("Could not save polygons.json after renaming {} for {}.", polygon.name(), owner.name(), exception);
			source.sendFailure(Component.literal("Could not save the renamed polygon: " + exception.getMessage()));
			return 0;
		}
		ArowsMod.info("{} renamed polygon {} to {} for {} in {} ({} points).",
				source.getTextName(), polygon.name(), newName, owner.name(), polygon.worldId(), polygon.points().size());
		visuals.renamed(polygon.ownerId(), polygon.name(), newName);
		source.sendSuccess(() -> Component.literal("Renamed " + polygon.name() + " to " + newName + " for " + owner.name() + "."), false);
		return 1;
	}

	private static int remove(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, Collection<NameAndId> profiles, String name, PolygonVisuals visuals) {
		NameAndId owner = onePlayer(source, profiles);
		if (owner == null) {
			return 0;
		}
		SavedPolygon polygon = store.find(owner.id(), name).orElse(null);
		if (polygon == null) {
			source.sendFailure(Component.literal(owner.name() + " has no polygon named " + name + "."));
			return 0;
		}
		try {
			bridge.hide(polygon);
		} catch (RuntimeException exception) {
			ArowsMod.warn("Could not remove marker {} for {} in {}.", polygon.name(), owner.name(), polygon.worldId(), exception);
			source.sendFailure(Component.literal("Pl3xMap could not remove this polygon: " + exception.getMessage()));
			return 0;
		}
		store.remove(polygon);
		visuals.removed(polygon.ownerId(), polygon.name());
		ArowsMod.info("{} removed polygon {} for {} in {} ({} points).",
				source.getTextName(), polygon.name(), owner.name(), polygon.worldId(), polygon.points().size());
		source.sendSuccess(() -> Component.literal("Removed " + polygon.name() + " from " + owner.name() + "."), false);
		return 1;
	}

	private static void logColorChanged(CommandSourceStack source, NameAndId owner, List<SavedPolygon> polygons, int restyled) {
		if (polygons.size() == 1) {
			SavedPolygon polygon = polygons.getFirst();
			ArowsMod.info("{} changed the color for {} on polygon {} in {} ({} points).",
					source.getTextName(), owner.name(), polygon.name(), polygon.worldId(), polygon.points().size());
			return;
		}
		ArowsMod.info("{} changed the color for {} ({} markers).", source.getTextName(), owner.name(), restyled);
	}

	private static ServerPlayer operator(CommandSourceStack source) {
		ServerPlayer operator = source.getPlayer();
		if (operator == null) {
			source.sendFailure(Component.literal("A player operator has to run this. The polygon uses your position and world."));
			return null;
		}
		return operator;
	}

	private static NameAndId onePlayer(CommandSourceStack source, Collection<NameAndId> profiles) {
		if (profiles.size() != 1) {
			source.sendFailure(Component.literal("Choose one player."));
			return null;
		}
		return profiles.iterator().next();
	}
}
