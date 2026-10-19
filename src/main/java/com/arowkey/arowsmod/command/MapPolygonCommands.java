package com.arowkey.arowsmod.command;

import com.arowkey.arowsmod.ArowsMod;
import com.arowkey.arowsmod.map.ArowsConfig;
import com.arowkey.arowsmod.map.MapPoint;
import com.arowkey.arowsmod.map.Pl3xMapBridge;
import com.arowkey.arowsmod.map.PlayerColor;
import com.arowkey.arowsmod.map.PolygonDraft;
import com.arowkey.arowsmod.map.PolygonStore;
import com.arowkey.arowsmod.map.SavedPolygon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.pl3x.map.core.markers.Point;

import java.util.Collection;
import java.util.List;

public final class MapPolygonCommands {
	private MapPolygonCommands() {
	}

	public static void register(ArowsConfig config, PolygonStore store, Pl3xMapBridge bridge) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				register(dispatcher, registryAccess, config, store, bridge));
	}

	/**
	 * Every polygon command and {@code /arowsmod reload} require operator permission
	 * ({@link Permissions#COMMANDS_GAMEMASTER}, level 2). Start, add, finish, and cancel also need
	 * a player position. List, remove, color, rename, and reload can run from the console.
	 */

	private static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext registryAccess,
			ArowsConfig config,
			PolygonStore store,
			Pl3xMapBridge bridge
	) {
		dispatcher.register(Commands.literal("arowsmod")
				.then(Commands.literal("reload")
						.requires(MapPolygonCommands::isOperator)
						.executes(context -> reload(context.getSource(), config, store, bridge)))
				.then(Commands.literal("map")
						.then(Commands.literal("polygon")
								.requires(MapPolygonCommands::isOperator)
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
								.then(Commands.literal("add")
										.executes(context -> addHere(context.getSource(), store))
										.then(Commands.argument("x", IntegerArgumentType.integer())
												.then(Commands.argument("z", IntegerArgumentType.integer())
														.executes(context -> addAt(
																context.getSource(),
																store,
																IntegerArgumentType.getInteger(context, "x"),
																IntegerArgumentType.getInteger(context, "z")
														)))))
								.then(Commands.literal("finish")
										.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
												.executes(context -> finish(
														context.getSource(),
														config,
														store,
														bridge,
														com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")
												))))
								.then(Commands.literal("cancel")
										.executes(context -> cancel(context.getSource(), store)))
								.then(Commands.literal("list")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.executes(context -> list(context.getSource(), store, profiles(context)))))
								.then(Commands.literal("remove")
										.then(Commands.argument("player", GameProfileArgument.gameProfile())
												.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
														.executes(context -> remove(
																context.getSource(),
																store,
																bridge,
																profiles(context),
																com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")
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
																		com.mojang.brigadier.arguments.StringArgumentType.getString(context, "new-name")
																)))))))));
	}

	private static boolean isOperator(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static int reload(CommandSourceStack source, ArowsConfig config, PolygonStore store, Pl3xMapBridge bridge) {
		String failure = config.reload();
		if (failure != null) {
			source.sendFailure(Component.literal(failure));
			return 0;
		}
		List<SavedPolygon> polygons = store.all();
		int waiting = 0;
		try {
			bridge.refreshLayerLabels();
			for (SavedPolygon polygon : polygons) {
				if (!bridge.show(polygon)) {
					waiting++;
				}
			}
		} catch (RuntimeException exception) {
			ArowsMod.LOGGER.error("Pl3xMap could not restyle polygons after config reload", exception);
			source.sendFailure(Component.literal("Reloaded the config, but Pl3xMap could not restyle the markers: " + exception.getMessage()));
			return 0;
		}
		int pending = waiting;
		int restyled = polygons.size() - pending;
		source.sendSuccess(() -> Component.literal("Reloaded config. Restyled " + restyled + " polygon" + (restyled == 1 ? "" : "s") + (pending == 0 ? "." : "; " + pending + " will update when their world loads on Pl3xMap.")), false);
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
		int waiting = 0;
		try {
			for (SavedPolygon polygon : polygons) {
				if (!bridge.show(polygon)) {
					waiting++;
				}
			}
		} catch (RuntimeException exception) {
			ArowsMod.LOGGER.error("Pl3xMap could not restyle polygons for {}", owner.name(), exception);
			source.sendFailure(Component.literal("Saved " + color + " for " + owner.name() + ", but Pl3xMap could not restyle the markers: " + exception.getMessage()));
			return 0;
		}
		int pending = waiting;
		int restyled = polygons.size() - pending;
		if (pending > 0) {
			source.sendSuccess(() -> Component.literal("Set " + owner.name() + " to " + color + ". Restyled " + restyled + " polygon" + (restyled == 1 ? "" : "s") + "; " + pending + " will update when their world loads on Pl3xMap."), false);
		} else {
			source.sendSuccess(() -> Component.literal("Set " + owner.name() + " to " + color + " and restyled " + restyled + " polygon" + (restyled == 1 ? "" : "s") + "."), false);
		}
		return 1;
	}

	private static int addHere(CommandSourceStack source, PolygonStore store) {
		ServerPlayer operator = operator(source);
		if (operator == null) {
			return 0;
		}
		Point point = Point.of(source.getPosition().x(), source.getPosition().z());
		return add(source, store, new MapPoint(point.x(), point.z()));
	}

	private static int addAt(CommandSourceStack source, PolygonStore store, int x, int z) {
		if (operator(source) == null) {
			return 0;
		}
		return add(source, store, new MapPoint(x, z));
	}

	private static int add(CommandSourceStack source, PolygonStore store, MapPoint point) {
		PolygonDraft draft = store.draftFor(source.getPlayer().getUUID());
		if (draft == null) {
			source.sendFailure(Component.literal("Start a polygon before adding points."));
			return 0;
		}
		String worldId = source.getLevel().dimension().identifier().toString();
		if (!draft.worldId().equals(worldId)) {
			source.sendFailure(Component.literal("This polygon is in " + draft.worldId() + ". Stay in that world to add points."));
			return 0;
		}
		draft.add(point);
		source.sendSuccess(() -> Component.literal("Added " + point.x() + ", " + point.z() + " (" + draft.size() + " points)."), false);
		return 1;
	}

	private static int finish(CommandSourceStack source, ArowsConfig config, PolygonStore store, Pl3xMapBridge bridge, String name) {
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
		SavedPolygon polygon = new SavedPolygon(draft.ownerId(), draft.ownerName(), name, draft.worldId(), draft.color(), draft.points());
		try {
			boolean shown = bridge.show(polygon);
			store.add(polygon);
			store.removeDraft(operator.getUUID());
			if (shown) {
				source.sendSuccess(() -> Component.literal("Created " + name + " for " + draft.ownerName() + " on the ArowsMod map layer."), false);
			} else {
				source.sendSuccess(() -> Component.literal("Saved " + name + " for " + draft.ownerName() + ". Pl3xMap is not showing " + draft.worldId() + " yet; the marker will be registered when that world loads."), false);
				ArowsMod.LOGGER.warn("Saved polygon {} for {} in {}, but Pl3xMap has not loaded that world yet.", name, draft.ownerName(), draft.worldId());
			}
			return 1;
		} catch (RuntimeException exception) {
			ArowsMod.LOGGER.error("Pl3xMap rejected polygon {} for {}", name, draft.ownerName(), exception);
			source.sendFailure(Component.literal("Pl3xMap could not register this polygon: " + exception.getMessage()));
			return 0;
		}
	}

	private static int cancel(CommandSourceStack source, PolygonStore store) {
		ServerPlayer operator = operator(source);
		if (operator == null) {
			return 0;
		}
		PolygonDraft draft = store.removeDraft(operator.getUUID());
		if (draft == null) {
			source.sendFailure(Component.literal("You do not have an in-progress polygon."));
			return 0;
		}
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

	/** The file is updated only after the map marker is replaced. A failed save puts the old marker back. */
	private static int rename(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, Collection<NameAndId> profiles, String name, String newName) {
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
			ArowsMod.LOGGER.error("Pl3xMap could not rename polygon {} for {}", polygon.name(), owner.name(), exception);
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
			ArowsMod.LOGGER.error("Could not save renamed polygon {} for {}", polygon.name(), owner.name(), exception);
			source.sendFailure(Component.literal("Could not save the renamed polygon: " + exception.getMessage()));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Renamed " + polygon.name() + " to " + newName + " for " + owner.name() + "."), false);
		return 1;
	}

	private static int remove(CommandSourceStack source, PolygonStore store, Pl3xMapBridge bridge, Collection<NameAndId> profiles, String name) {
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
			ArowsMod.LOGGER.error("Pl3xMap could not remove polygon {} for {}", polygon.name(), owner.name(), exception);
			source.sendFailure(Component.literal("Pl3xMap could not remove this polygon: " + exception.getMessage()));
			return 0;
		}
		store.remove(polygon);
		source.sendSuccess(() -> Component.literal("Removed " + polygon.name() + " from " + owner.name() + "."), false);
		return 1;
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
