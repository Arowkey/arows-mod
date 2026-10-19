package com.arowkey.arowsmod.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PolygonStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final List<SavedPolygon> polygons = new ArrayList<>();
	private final Map<UUID, PolygonDraft> drafts = new HashMap<>();
	private final Map<UUID, String> colors = new HashMap<>();

	public PolygonStore() {
		this.path = FabricLoader.getInstance().getConfigDir().resolve("arowsmod").resolve("polygons.json");
	}

	public synchronized void load() {
		this.polygons.clear();
		this.colors.clear();
		if (Files.notExists(this.path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(this.path)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("players")) {
				JsonObject players = root.getAsJsonObject("players");
				for (String ownerId : players.keySet()) {
					String color = PlayerColor.parse(players.get(ownerId).getAsString());
					if (color == null) {
						throw new IllegalStateException(this.path + " has an invalid color for " + ownerId + ".");
					}
					this.colors.put(UUID.fromString(ownerId), color);
				}
			}
			JsonArray array = root.getAsJsonArray("polygons");
			if (array == null) {
				throw new IllegalStateException(this.path + " is missing the polygons list.");
			}
			for (var element : array) {
				JsonObject json = element.getAsJsonObject();
				List<MapPoint> points = new ArrayList<>();
				for (var pointElement : json.getAsJsonArray("points")) {
					JsonObject point = pointElement.getAsJsonObject();
					points.add(new MapPoint(point.get("x").getAsInt(), point.get("z").getAsInt()));
				}
				UUID ownerId = UUID.fromString(json.get("ownerId").getAsString());
				String color = PlayerColor.parse(json.get("color").getAsString());
				if (color == null) {
					throw new IllegalStateException(this.path + " has an invalid color on polygon " + json.get("name") + ".");
				}
				this.colors.putIfAbsent(ownerId, color);
				this.polygons.add(new SavedPolygon(
						ownerId,
						json.get("ownerName").getAsString(),
						json.get("name").getAsString(),
						json.get("world").getAsString(),
						color,
						List.copyOf(points)
				));
			}
		} catch (RuntimeException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read saved ArowsMod polygons at " + this.path, exception);
		}
	}

	public synchronized Optional<String> colorOf(UUID ownerId) {
		return Optional.ofNullable(this.colors.get(ownerId));
	}

	public synchronized void rememberColor(UUID ownerId, String color) {
		this.colors.put(ownerId, color);
		save();
	}

	public synchronized List<SavedPolygon> recolor(UUID ownerId, String color) {
		this.colors.put(ownerId, color);
		List<SavedPolygon> updated = new ArrayList<>();
		for (int index = 0; index < this.polygons.size(); index++) {
			SavedPolygon polygon = this.polygons.get(index);
			if (!polygon.ownerId().equals(ownerId)) {
				continue;
			}
			SavedPolygon next = new SavedPolygon(
					polygon.ownerId(),
					polygon.ownerName(),
					polygon.name(),
					polygon.worldId(),
					color,
					polygon.points()
			);
			this.polygons.set(index, next);
			updated.add(next);
		}
		for (PolygonDraft draft : this.drafts.values()) {
			if (draft.ownerId().equals(ownerId)) {
				draft.setColor(color);
			}
		}
		save();
		return updated;
	}

	public synchronized PolygonDraft draftFor(UUID operatorId) {
		return this.drafts.get(operatorId);
	}

	public synchronized void putDraft(UUID operatorId, PolygonDraft draft) {
		this.drafts.put(operatorId, draft);
	}

	public synchronized PolygonDraft removeDraft(UUID operatorId) {
		return this.drafts.remove(operatorId);
	}

	public synchronized int countFinished(UUID ownerId) {
		int count = 0;
		for (SavedPolygon polygon : this.polygons) {
			if (polygon.ownerId().equals(ownerId)) {
				count++;
			}
		}
		return count;
	}

	public synchronized boolean nameTaken(UUID ownerId, String name) {
		return find(ownerId, name).isPresent();
	}

	public synchronized List<SavedPolygon> all() {
		return List.copyOf(this.polygons);
	}

	public synchronized List<SavedPolygon> byOwner(UUID ownerId) {
		List<SavedPolygon> owned = new ArrayList<>();
		for (SavedPolygon polygon : this.polygons) {
			if (polygon.ownerId().equals(ownerId)) {
				owned.add(polygon);
			}
		}
		return owned;
	}

	public synchronized List<SavedPolygon> byWorld(String worldId) {
		List<SavedPolygon> inWorld = new ArrayList<>();
		for (SavedPolygon polygon : this.polygons) {
			if (polygon.worldId().equals(worldId)) {
				inWorld.add(polygon);
			}
		}
		return inWorld;
	}

	public synchronized Optional<SavedPolygon> find(UUID ownerId, String name) {
		for (SavedPolygon polygon : this.polygons) {
			if (polygon.ownerId().equals(ownerId) && polygon.name().equalsIgnoreCase(name)) {
				return Optional.of(polygon);
			}
		}
		return Optional.empty();
	}

	public synchronized void add(SavedPolygon polygon) {
		this.polygons.add(polygon);
		save();
	}

	public synchronized boolean remove(SavedPolygon polygon) {
		boolean removed = this.polygons.remove(polygon);
		if (removed) {
			save();
		}
		return removed;
	}

	public synchronized void replace(SavedPolygon previous, SavedPolygon next) {
		int index = this.polygons.indexOf(previous);
		if (index < 0) {
			throw new IllegalStateException("That polygon is no longer saved.");
		}
		this.polygons.set(index, next);
		save();
	}

	private void save() {
		JsonArray array = new JsonArray();
		for (SavedPolygon polygon : this.polygons) {
			JsonObject json = new JsonObject();
			json.addProperty("ownerId", polygon.ownerId().toString());
			json.addProperty("ownerName", polygon.ownerName());
			json.addProperty("name", polygon.name());
			json.addProperty("world", polygon.worldId());
			json.addProperty("color", polygon.color());
			JsonArray points = new JsonArray();
			for (MapPoint point : polygon.points()) {
				JsonObject pointJson = new JsonObject();
				pointJson.addProperty("x", point.x());
				pointJson.addProperty("z", point.z());
				points.add(pointJson);
			}
			json.add("points", points);
			array.add(json);
		}
		JsonObject root = new JsonObject();
		JsonObject players = new JsonObject();
		for (Map.Entry<UUID, String> entry : this.colors.entrySet()) {
			players.addProperty(entry.getKey().toString(), entry.getValue());
		}
		root.add("players", players);
		root.add("polygons", array);
		try {
			Files.createDirectories(this.path.getParent());
			Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temporary)) {
				GSON.toJson(root, writer);
			}
			try {
				Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not save ArowsMod polygons to " + this.path, exception);
		}
	}
}
