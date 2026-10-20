package com.arowkey.arowsmod.map;

import com.arowkey.arowsmod.ArowsMod;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block and text displays for a polygon an operator is drawing or has asked to see.
 * A draft uses the feet height stored with each point. show uses one Y for every marker.
 * The edge back to the first point is magenta concrete only while the polygon is in progress.
 * Displays are discarded instead of saved, including when their chunk unloads.
 */
public final class PolygonVisuals {
	/** Viewer id for a preview started from the server console. */
	public static final UUID CONSOLE_VIEWER = new UUID(0L, 0L);
	private static final int SHOW_TICKS = 3 * 60 * 20;
	private static final float CORNER_SIZE = 0.5f;
	private static final float TEXT_SCALE = 0.7f;
	private static final float TEXT_GAP = 0.15f;
	private static final float EDGE_THICKNESS = 0.12f;
	/** Multiplied by 64 in the client, so 2 is about 128 blocks. */
	private static final float VIEW_RANGE = 2f;

	private final Map<UUID, List<Display>> drafts = new ConcurrentHashMap<>();
	private final Map<UUID, Shown> shows = new ConcurrentHashMap<>();
	private int tick;

	public void start() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			this.tick++;
			this.expireShows(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> this.clearAll());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> this.clearOperator(handler.getPlayer().getUUID()));
		ServerChunkEvents.CHUNK_UNLOAD.register(this::discardInChunk);
	}

	public void showDraft(ServerPlayer operator, PolygonDraft draft) {
		this.clearDraft(operator.getUUID());
		if (draft.size() == 0) {
			return;
		}
		List<MarkerPoint> points = new ArrayList<>();
		for (DraftPoint point : draft.points()) {
			points.add(new MarkerPoint(point.x(), point.y(), point.z()));
		}
		this.drafts.put(operator.getUUID(), this.spawn(operator.level(), points, true));
	}

	public void clearDraft(UUID operatorId) {
		discard(this.drafts.remove(operatorId));
	}

	/**
	 * Shows a finished polygon. Every marker uses the same {@code y}. Unloaded chunks are skipped.
	 * Returns the 1-based indexes that were skipped, or null when the world is missing.
	 */
	public List<Integer> showSaved(MinecraftServer server, UUID viewerId, SavedPolygon polygon, int y) {
		this.clearShow(viewerId);
		ServerLevel level = level(server, polygon.worldId());
		if (level == null) {
			return null;
		}
		List<MarkerPoint> points = new ArrayList<>();
		List<Integer> skipped = new ArrayList<>();
		for (int index = 0; index < polygon.points().size(); index++) {
			MapPoint point = polygon.points().get(index);
			int chunkX = SectionPos.blockToSectionCoord(point.x());
			int chunkZ = SectionPos.blockToSectionCoord(point.z());
			if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
				skipped.add(index + 1);
				points.add(null);
				continue;
			}
			points.add(new MarkerPoint(point.x(), y, point.z()));
		}
		List<Display> entities = this.spawn(level, points, false);
		if (!entities.isEmpty()) {
			this.shows.put(viewerId, new Shown(polygon.ownerId(), polygon.name(), y, entities, this.tick + SHOW_TICKS));
		}
		return skipped;
	}

	public void clearShow(UUID operatorId) {
		Shown shown = this.shows.remove(operatorId);
		if (shown != null) {
			discard(shown.entities);
		}
	}

	public void refresh(SavedPolygon polygon, MinecraftServer server) {
		List<Map.Entry<UUID, Shown>> matching = new ArrayList<>();
		for (Map.Entry<UUID, Shown> entry : this.shows.entrySet()) {
			Shown shown = entry.getValue();
			if (shown.ownerId.equals(polygon.ownerId()) && shown.name.equalsIgnoreCase(polygon.name())) {
				matching.add(Map.entry(entry.getKey(), shown));
			}
		}
		for (Map.Entry<UUID, Shown> entry : matching) {
			UUID viewerId = entry.getKey();
			if (!viewerId.equals(CONSOLE_VIEWER) && server.getPlayerList().getPlayer(viewerId) == null) {
				this.clearShow(viewerId);
				continue;
			}
			this.showSaved(server, viewerId, polygon, entry.getValue().y);
		}
	}

	public void renamed(UUID ownerId, String previousName, String newName) {
		for (Shown shown : this.shows.values()) {
			if (shown.ownerId.equals(ownerId) && shown.name.equalsIgnoreCase(previousName)) {
				shown.name = newName;
			}
		}
	}

	public void removed(UUID ownerId, String name) {
		Iterator<Shown> shows = this.shows.values().iterator();
		while (shows.hasNext()) {
			Shown shown = shows.next();
			if (shown.ownerId.equals(ownerId) && shown.name.equalsIgnoreCase(name)) {
				discard(shown.entities);
				shows.remove();
			}
		}
	}

	private void clearOperator(UUID operatorId) {
		this.clearDraft(operatorId);
		this.clearShow(operatorId);
	}

	private void clearAll() {
		for (List<Display> entities : this.drafts.values()) {
			discard(entities);
		}
		this.drafts.clear();
		for (Shown shown : this.shows.values()) {
			discard(shown.entities);
		}
		this.shows.clear();
	}

	private void expireShows(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Shown>> shows = this.shows.entrySet().iterator();
		while (shows.hasNext()) {
			Map.Entry<UUID, Shown> entry = shows.next();
			if (this.tick < entry.getValue().expiresAtTick) {
				continue;
			}
			discard(entry.getValue().entities);
			shows.remove();
			ServerPlayer operator = server.getPlayerList().getPlayer(entry.getKey());
			if (operator != null) {
				operator.sendSystemMessage(Component.literal("Hid the polygon preview after 3 minutes."));
			}
		}
	}

	private void discardInChunk(ServerLevel level, LevelChunk chunk) {
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();
		for (List<Display> entities : this.drafts.values()) {
			discardInChunk(entities, level, chunkX, chunkZ);
		}
		for (Shown shown : this.shows.values()) {
			discardInChunk(shown.entities, level, chunkX, chunkZ);
		}
	}

	private static void discardInChunk(List<Display> entities, ServerLevel level, int chunkX, int chunkZ) {
		Iterator<Display> iterator = entities.iterator();
		while (iterator.hasNext()) {
			Display entity = iterator.next();
			if (entity.level() == level && entity.chunkPosition().x() == chunkX && entity.chunkPosition().z() == chunkZ) {
				if (!entity.isRemoved()) {
					entity.discard();
				}
				iterator.remove();
			}
		}
	}

	private List<Display> spawn(ServerLevel level, List<MarkerPoint> points, boolean closingEdge) {
		List<Display> entities = new ArrayList<>();
		String failure = null;
		boolean tried = false;
		int count = points.size();
		for (int index = 0; index < count; index++) {
			MarkerPoint point = points.get(index);
			if (point == null) {
				continue;
			}
			tried = true;
			Placed corner = blockDisplay(level, point.x() + 0.5, point.y(), point.z() + 0.5, cornerBlock(index, count), cornerTransformation());
			if (corner.entity != null) {
				entities.add(corner.entity);
			} else {
				failure = firstFailure(failure, corner.failure);
			}
			Placed label = textDisplay(level, point.x() + 0.5, point.y() + CORNER_SIZE + TEXT_GAP, point.z() + 0.5, Integer.toString(index + 1));
			if (label.entity != null) {
				entities.add(label.entity);
			} else {
				failure = firstFailure(failure, label.failure);
			}
		}
		if (count >= 2) {
			for (int index = 0; index < count; index++) {
				MarkerPoint from = points.get(index);
				MarkerPoint to = points.get((index + 1) % count);
				if (from == null || to == null) {
					continue;
				}
				tried = true;
				boolean closing = closingEdge && index == count - 1;
				Placed edge = edgeDisplay(level, from, to, closing ? Blocks.CONCRETE.magenta() : Blocks.CONCRETE.lightBlue());
				if (edge.entity != null) {
					entities.add(edge.entity);
				} else if (edge.failure != null) {
					failure = firstFailure(failure, edge.failure);
				}
			}
		}
		if (tried && entities.isEmpty()) {
			ArowsMod.warn("Could not show polygon displays: {}.", failure == null ? "no entities were added" : failure);
		}
		return entities;
	}

	private static String firstFailure(String current, String next) {
		return current == null ? next : current;
	}

	private static Block cornerBlock(int index, int count) {
		if (index == count - 1) {
			return Blocks.CONCRETE.orange();
		}
		if (index == 0) {
			return Blocks.CONCRETE.lime();
		}
		return Blocks.CONCRETE.white();
	}

	private static Transformation cornerTransformation() {
		float offset = -CORNER_SIZE / 2f;
		return new Transformation(new Vector3f(offset, 0f, offset), new Quaternionf(), new Vector3f(CORNER_SIZE, CORNER_SIZE, CORNER_SIZE), new Quaternionf());
	}

	private static Placed edgeDisplay(ServerLevel level, MarkerPoint from, MarkerPoint to, Block block) {
		float dx = to.x() - from.x();
		float dy = to.y() - from.y();
		float dz = to.z() - from.z();
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 0.001f) {
			return Placed.skipped();
		}
		Vector3f direction = new Vector3f(dx, dy, dz).normalize();
		Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), direction);
		Vector3f offset = rotation.transform(new Vector3f(-EDGE_THICKNESS / 2f, 0f, -EDGE_THICKNESS / 2f));
		Transformation transformation = new Transformation(offset, rotation, new Vector3f(EDGE_THICKNESS, length, EDGE_THICKNESS), new Quaternionf());
		double centerY = from.y() + CORNER_SIZE / 2.0;
		return blockDisplay(level, from.x() + 0.5, centerY, from.z() + 0.5, block, transformation);
	}

	private static Placed blockDisplay(ServerLevel level, double x, double y, double z, Block block, Transformation transformation) {
		String scale = scaleProblem(transformation, "block");
		if (scale != null) {
			return Placed.failed(scale);
		}
		BlockState state = block.defaultBlockState();
		if (state.isAir()) {
			return Placed.failed("the block display has no block");
		}
		Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		TagValueOutput output = displayData(level.registryAccess(), x, y, z, transformation);
		output.store("block_state", BlockState.CODEC, state);
		return place(level, display, x, y, z, output.buildResult(), "block");
	}

	private static Placed textDisplay(ServerLevel level, double x, double y, double z, String text) {
		Transformation transformation = new Transformation(new Vector3f(0f, 0f, 0f), new Quaternionf(), new Vector3f(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE), new Quaternionf());
		String scale = scaleProblem(transformation, "text");
		if (scale != null) {
			return Placed.failed(scale);
		}
		Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		TagValueOutput output = displayData(level.registryAccess(), x, y, z, transformation);
		output.store("text", ComponentSerialization.CODEC, Component.literal(text));
		output.store("billboard", Display.BillboardConstraints.CODEC, Display.BillboardConstraints.CENTER);
		return place(level, display, x, y, z, output.buildResult(), "text");
	}

	/**
	 * Writes the tags Display and the entity load path actually read.
	 * Position has to be in the tag: load replaces a missing Pos with the world origin.
	 */
	private static TagValueOutput displayData(RegistryAccess registries, double x, double y, double z, Transformation transformation) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.store("Pos", Vec3.CODEC, new Vec3(x, y, z));
		output.store("transformation", Transformation.EXTENDED_CODEC, transformation);
		output.putFloat("view_range", VIEW_RANGE);
		output.putFloat("shadow_radius", 0f);
		output.putBoolean("NoGravity", true);
		return output;
	}

	private static Placed place(ServerLevel level, Display display, double x, double y, double z, CompoundTag data, String kind) {
		String loaded = load(display, level.registryAccess(), data);
		if (loaded != null) {
			return Placed.failed(loaded);
		}
		// load() already applied Pos. Set it again so a discarded Pos tag cannot leave the entity at the origin.
		display.setPos(x, y, z);
		if (!near(display.getX(), x) || !near(display.getY(), y) || !near(display.getZ(), z)) {
			return Placed.failed("the " + kind + " display stayed at "
					+ display.getX() + ", " + display.getY() + ", " + display.getZ()
					+ " instead of the marker");
		}
		String invalid = unreadableBlock(display, level.registryAccess());
		if (invalid != null) {
			return Placed.failed(invalid);
		}
		int chunkX = SectionPos.blockToSectionCoord(display.blockPosition().getX());
		int chunkZ = SectionPos.blockToSectionCoord(display.blockPosition().getZ());
		if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
			return Placed.failed("the chunk for the " + kind + " display is not loaded");
		}
		if (!level.addFreshEntity(display)) {
			return Placed.failed("the world did not add the " + kind + " display");
		}
		if (display.isRemoved()) {
			return Placed.failed("the " + kind + " display was removed on the same tick");
		}
		return new Placed(display, null);
	}

	private static String scaleProblem(Transformation transformation, String kind) {
		Vector3fc scale = transformation.scale();
		if (Math.abs(scale.x()) < 0.001f || Math.abs(scale.y()) < 0.001f || Math.abs(scale.z()) < 0.001f) {
			return "the " + kind + " display scale is zero";
		}
		return null;
	}

	/** Block displays stay invisible when the saved block does not decode, so confirm it stuck. */
	private static String unreadableBlock(Display display, RegistryAccess registries) {
		if (!(display instanceof Display.BlockDisplay)) {
			return null;
		}
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		display.saveWithoutId(output);
		ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult());
		BlockState state = input.read("block_state", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
		if (state.isAir()) {
			return "the block display has no block";
		}
		return null;
	}

	private static boolean near(double actual, double expected) {
		return Math.abs(actual - expected) < 0.01;
	}

	/** Returns a reason when the entity could not be loaded. */
	private static String load(Display display, RegistryAccess registries, CompoundTag data) {
		try {
			ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, registries, data);
			display.load(input);
			return null;
		} catch (RuntimeException exception) {
			String message = exception.getMessage();
			return message == null ? "could not read the display data" : "could not read the display data: " + message;
		}
	}

	private static void discard(List<Display> entities) {
		if (entities == null) {
			return;
		}
		for (Display entity : entities) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}
		entities.clear();
	}

	private static ServerLevel level(MinecraftServer server, String worldId) {
		try {
			Identifier identifier = Identifier.parse(worldId);
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, identifier);
			return server.getLevel(key);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private record MarkerPoint(int x, int y, int z) {
	}

	/** A display that was added, a spawn that was not attempted, or the reason nothing was added. */
	private record Placed(Display entity, String failure) {
		private static Placed failed(String failure) {
			return new Placed(null, failure);
		}

		private static Placed skipped() {
			return new Placed(null, null);
		}
	}

	private static final class Shown {
		private final UUID ownerId;
		private String name;
		private final int y;
		private final List<Display> entities;
		private final int expiresAtTick;

		private Shown(UUID ownerId, String name, int y, List<Display> entities, int expiresAtTick) {
			this.ownerId = ownerId;
			this.name = name;
			this.y = y;
			this.entities = entities;
			this.expiresAtTick = expiresAtTick;
		}
	}
}
