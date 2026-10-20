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
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block and text displays for a polygon an operator is drawing or has asked to see.
 * They are discarded instead of saved, including when their chunk unloads.
 */
public final class PolygonVisuals {
	private static final int SHOW_TICKS = 3 * 60 * 20;
	private static final float CORNER_SIZE = 0.35f;
	private static final float EDGE_THICKNESS = 0.12f;

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
		this.drafts.put(operator.getUUID(), this.spawn(operator.level(), points));
	}

	public void clearDraft(UUID operatorId) {
		discard(this.drafts.remove(operatorId));
	}

	/**
	 * Shows a finished polygon. Y is the top solid block plus one, which is what
	 * {@link Heightmap.Types#OCEAN_FLOOR} already stores. Unloaded chunks are skipped.
	 * Returns the 1-based indexes that were skipped, or null when the world is missing.
	 */
	public List<Integer> showSaved(ServerPlayer operator, SavedPolygon polygon) {
		this.clearShow(operator.getUUID());
		ServerLevel level = level(operator.level().getServer(), polygon.worldId());
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
			int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, point.x(), point.z());
			points.add(new MarkerPoint(point.x(), y, point.z()));
		}
		List<Display> entities = this.spawn(level, points);
		if (!entities.isEmpty()) {
			this.shows.put(operator.getUUID(), new Shown(polygon.ownerId(), polygon.name(), entities, this.tick + SHOW_TICKS));
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
		List<UUID> operators = new ArrayList<>();
		for (Map.Entry<UUID, Shown> entry : this.shows.entrySet()) {
			Shown shown = entry.getValue();
			if (shown.ownerId.equals(polygon.ownerId()) && shown.name.equalsIgnoreCase(polygon.name())) {
				operators.add(entry.getKey());
			}
		}
		for (UUID operatorId : operators) {
			ServerPlayer operator = server.getPlayerList().getPlayer(operatorId);
			if (operator == null) {
				this.clearShow(operatorId);
				continue;
			}
			this.showSaved(operator, polygon);
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

	private List<Display> spawn(ServerLevel level, List<MarkerPoint> points) {
		List<Display> entities = new ArrayList<>();
		int count = points.size();
		for (int index = 0; index < count; index++) {
			MarkerPoint point = points.get(index);
			if (point == null) {
				continue;
			}
			Display corner = blockDisplay(level, point.x() + 0.5, point.y(), point.z() + 0.5, cornerBlock(index, count), cornerTransformation());
			if (corner != null) {
				entities.add(corner);
			}
			Display label = textDisplay(level, point.x() + 0.5, point.y() + 0.55, point.z() + 0.5, Integer.toString(index + 1));
			if (label != null) {
				entities.add(label);
			}
		}
		if (count >= 2) {
			for (int index = 0; index < count; index++) {
				MarkerPoint from = points.get(index);
				MarkerPoint to = points.get((index + 1) % count);
				if (from == null || to == null) {
					continue;
				}
				Display edge = edgeDisplay(level, from, to);
				if (edge != null) {
					entities.add(edge);
				}
			}
		}
		return entities;
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

	private static Display edgeDisplay(ServerLevel level, MarkerPoint from, MarkerPoint to) {
		float dx = to.x() - from.x();
		float dy = to.y() - from.y();
		float dz = to.z() - from.z();
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 0.001f) {
			return null;
		}
		Vector3f direction = new Vector3f(dx, dy, dz).normalize();
		Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), direction);
		Vector3f offset = rotation.transform(new Vector3f(-EDGE_THICKNESS / 2f, 0f, -EDGE_THICKNESS / 2f));
		Transformation transformation = new Transformation(offset, rotation, new Vector3f(EDGE_THICKNESS, length, EDGE_THICKNESS), new Quaternionf());
		return blockDisplay(level, from.x() + 0.5, from.y(), from.z() + 0.5, Blocks.CONCRETE.lightBlue(), transformation);
	}

	private static Display blockDisplay(ServerLevel level, double x, double y, double z, Block block, Transformation transformation) {
		Display display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		display.setPos(x, y, z);
		CompoundTag data = new CompoundTag();
		data.put("block_state", NbtUtils.writeBlockState(block.defaultBlockState()));
		data.put("transformation", transformationTag(transformation));
		data.putFloat("shadow_radius", 0f);
		data.putFloat("view_range", 2f);
		if (!load(display, level.registryAccess(), data)) {
			return null;
		}
		if (!level.addFreshEntity(display)) {
			return null;
		}
		return display;
	}

	private static Display textDisplay(ServerLevel level, double x, double y, double z, String text) {
		Display display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		display.setPos(x, y, z);
		CompoundTag data = new CompoundTag();
		RegistryAccess registries = level.registryAccess();
		Tag encoded = ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), Component.literal(text)).result().orElse(null);
		if (encoded == null) {
			return null;
		}
		data.put("text", encoded);
		data.putString("billboard", "center");
		data.put("transformation", transformationTag(new Transformation(new Vector3f(-0.15f, 0f, -0.15f), new Quaternionf(), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf())));
		data.putFloat("shadow_radius", 0f);
		data.putFloat("view_range", 2f);
		if (!load(display, registries, data)) {
			return null;
		}
		if (!level.addFreshEntity(display)) {
			return null;
		}
		return display;
	}

	private static Tag transformationTag(Transformation transformation) {
		return Transformation.CODEC.encodeStart(NbtOps.INSTANCE, transformation).getOrThrow();
	}

	private static boolean load(Display display, RegistryAccess registries, CompoundTag data) {
		try {
			ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, registries, data);
			display.load(input);
			return true;
		} catch (RuntimeException exception) {
			ArowsMod.warn("Could not create a polygon point display.", exception);
			return false;
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
		Identifier identifier = Identifier.parse(worldId);
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, identifier);
		return server.getLevel(key);
	}

	private record MarkerPoint(int x, int y, int z) {
	}

	private static final class Shown {
		private final UUID ownerId;
		private String name;
		private final List<Display> entities;
		private final int expiresAtTick;

		private Shown(UUID ownerId, String name, List<Display> entities, int expiresAtTick) {
			this.ownerId = ownerId;
			this.name = name;
			this.entities = entities;
			this.expiresAtTick = expiresAtTick;
		}
	}
}
