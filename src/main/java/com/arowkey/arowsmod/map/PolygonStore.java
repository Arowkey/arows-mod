package com.arowkey.arowsmod.map;

import com.arowkey.arowsmod.ArowsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
		// Fixed child of the config directory. Polygon names are never used as path segments.
		this.path = ArowsConfig.configPath("arowsmod", "polygons.json");
	}

	/**
	 * Reads polygons into temporary lists and swaps them in only after the whole file parses.
	 * A bad file leaves the previous in-memory polygons and colors in place and is not rewritten.
	 * In-progress drafts are not in this file.
	 */
	public synchronized void load() {
		if (Files.notExists(this.path)) {
			return;
		}
		List<SavedPolygon> loadedPolygons = new ArrayList<>();
		Map<UUID, String> loadedColors = new HashMap<>();
		try (Reader reader = Files.newBufferedReader(this.path)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("players")) {
				JsonObject players = root.getAsJsonObject("players");
				for (String ownerId : players.keySet()) {
					String color = PlayerColor.parse(players.get(ownerId).getAsString());
					if (color == null) {
						throw new IllegalStateException(this.path + " has an invalid color for " + ownerId + ".");
					}
					loadedColors.put(UUID.fromString(ownerId), color);
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
				loadedColors.putIfAbsent(ownerId, color);
				loadedPolygons.add(new SavedPolygon(
						ownerId,
						json.get("ownerName").getAsString(),
						json.get("name").getAsString(),
						json.get("world").getAsString(),
						color,
						List.copyOf(points)
				));
			}
		} catch (RuntimeException exception) {
			ArowsMod.error("Could not read polygons.json.", exception);
			throw exception;
		} catch (Exception exception) {
			ArowsMod.error("Could not read polygons.json.", exception);
			throw new IllegalStateException("Could not read saved ArowsMod polygons at " + this.path, exception);
		}
		this.polygons.clear();
		this.polygons.addAll(loadedPolygons);
		this.colors.clear();
		this.colors.putAll(loadedColors);
	}

	public synchronized Optional<String> colorOf(UUID ownerId) {
		return Optional.ofNullable(this.colors.get(ownerId));
	}

	public synchronized void rememberColor(UUID ownerId, String color) {
		String previous = this.colors.put(ownerId, color);
		saveOrRevert(() -> {
			if (previous == null) {
				this.colors.remove(ownerId);
			} else {
				this.colors.put(ownerId, previous);
			}
		});
	}

	public synchronized List<SavedPolygon> recolor(UUID ownerId, String color) {
		Map<UUID, String> colorsBefore = new HashMap<>(this.colors);
		List<SavedPolygon> polygonsBefore = new ArrayList<>(this.polygons);
		Map<UUID, String> draftColors = new HashMap<>();
		for (Map.Entry<UUID, PolygonDraft> entry : this.drafts.entrySet()) {
			draftColors.put(entry.getKey(), entry.getValue().color());
		}
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
		saveOrRevert(() -> {
			this.colors.clear();
			this.colors.putAll(colorsBefore);
			this.polygons.clear();
			this.polygons.addAll(polygonsBefore);
			for (Map.Entry<UUID, PolygonDraft> entry : this.drafts.entrySet()) {
				entry.getValue().setColor(draftColors.get(entry.getKey()));
			}
		});
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

	public synchronized int polygonCount() {
		return this.polygons.size();
	}

	public synchronized int colorCount() {
		return this.colors.size();
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
		saveOrRevert(() -> this.polygons.remove(polygon));
	}

	public synchronized boolean remove(SavedPolygon polygon) {
		boolean removed = this.polygons.remove(polygon);
		if (removed) {
			saveOrRevert(() -> this.polygons.add(polygon));
		}
		return removed;
	}

	public synchronized void replace(SavedPolygon previous, SavedPolygon next) {
		int index = this.polygons.indexOf(previous);
		if (index < 0) {
			throw new IllegalStateException("That polygon is no longer saved.");
		}
		this.polygons.set(index, next);
		saveOrRevert(() -> this.polygons.set(index, previous));
	}

	/** A failed write puts the in-memory polygons and colors back. The file is left unchanged. */
	private void saveOrRevert(Runnable revert) {
		try {
			save();
		} catch (RuntimeException exception) {
			revert.run();
			throw exception;
		}
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
			// Write a sibling temp file, then replace polygons.json. A failed write leaves the old file.
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
			ArowsMod.warn("Could not save polygons.json.", exception);
			throw new IllegalStateException("Could not save ArowsMod polygons to " + this.path, exception);
		}
	}
}
