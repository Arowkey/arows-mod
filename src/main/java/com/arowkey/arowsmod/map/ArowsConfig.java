package com.arowkey.arowsmod.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArowsConfig {
	public static final String MAX_POLYGONS_KEY = "max-polygons-per-player";
	public static final String STROKE_WEIGHT_KEY = "stroke-weight";
	public static final String STROKE_OPACITY_KEY = "stroke-opacity";
	public static final String FILL_OPACITY_KEY = "fill-opacity";

	public static final int DEFAULT_MAX_POLYGONS = 10;
	public static final int DEFAULT_STROKE_WEIGHT = 3;
	public static final double DEFAULT_STROKE_OPACITY = 1.0;
	public static final double DEFAULT_FILL_OPACITY = 0.35;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private volatile Snapshot snapshot;

	private ArowsConfig(Snapshot snapshot) {
		this.snapshot = snapshot;
	}

	public int maxPolygonsPerPlayer() {
		return this.snapshot.maxPolygonsPerPlayer;
	}

	public int strokeWeight() {
		return this.snapshot.strokeWeight;
	}

	public double strokeOpacity() {
		return this.snapshot.strokeOpacity;
	}

	public double fillOpacity() {
		return this.snapshot.fillOpacity;
	}

	public int strokeColor(int rgb) {
		return withOpacity(rgb, strokeOpacity());
	}

	public int fillColor(int rgb) {
		return withOpacity(rgb, fillOpacity());
	}

	public static int withOpacity(int rgb, double opacity) {
		int alpha = (int) Math.round(opacity * 255.0);
		alpha = Math.min(255, Math.max(0, alpha));
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	public static ArowsConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("arowsmod.json");
		try {
			JsonObject json = new JsonObject();
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					json = JsonParser.parseReader(reader).getAsJsonObject();
				}
			}
			boolean dirty = false;
			dirty |= putDefault(json, MAX_POLYGONS_KEY, new JsonPrimitive(DEFAULT_MAX_POLYGONS));
			dirty |= putDefault(json, STROKE_WEIGHT_KEY, new JsonPrimitive(DEFAULT_STROKE_WEIGHT));
			dirty |= putDefault(json, STROKE_OPACITY_KEY, new JsonPrimitive(DEFAULT_STROKE_OPACITY));
			dirty |= putDefault(json, FILL_OPACITY_KEY, new JsonPrimitive(DEFAULT_FILL_OPACITY));
			int maxPolygons = readNonNegativeInt(json, MAX_POLYGONS_KEY, path);
			int strokeWeight = readNonNegativeInt(json, STROKE_WEIGHT_KEY, path);
			double strokeOpacity = readOpacity(json, STROKE_OPACITY_KEY, path);
			double fillOpacity = readOpacity(json, FILL_OPACITY_KEY, path);
			if (Files.notExists(path) || dirty) {
				writeJson(path, json);
			}
			return new ArowsConfig(new Snapshot(maxPolygons, strokeWeight, strokeOpacity, fillOpacity));
		} catch (RuntimeException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read ArowsMod config at " + path, exception);
		}
	}

	public String reload() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("arowsmod.json");
		if (Files.notExists(path)) {
			return path + " is missing. Kept the current config.";
		}
		try {
			JsonElement parsed;
			try (Reader reader = Files.newBufferedReader(path)) {
				parsed = JsonParser.parseReader(reader);
			}
			if (parsed == null || !parsed.isJsonObject()) {
				return path + " must be a JSON object. Kept the current config.";
			}
			JsonObject json = parsed.getAsJsonObject();
			Snapshot next = new Snapshot(
					readNonNegativeInt(json, MAX_POLYGONS_KEY, path),
					readNonNegativeInt(json, STROKE_WEIGHT_KEY, path),
					readOpacity(json, STROKE_OPACITY_KEY, path),
					readOpacity(json, FILL_OPACITY_KEY, path)
			);
			this.snapshot = next;
			return null;
		} catch (RuntimeException exception) {
			return exception.getMessage() + " Kept the current config.";
		} catch (Exception exception) {
			return "Could not read " + path + ": " + exception.getMessage() + " Kept the current config.";
		}
	}

	private record Snapshot(int maxPolygonsPerPlayer, int strokeWeight, double strokeOpacity, double fillOpacity) {
	}

	private static boolean putDefault(JsonObject json, String key, JsonElement value) {
		if (json.has(key)) {
			return false;
		}
		json.add(key, value);
		return true;
	}

	private static int readNonNegativeInt(JsonObject json, String key, Path path) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalStateException(path + " " + key + " must be an integer.");
		}
		double value = element.getAsDouble();
		if (value < 0 || value != Math.rint(value) || value > Integer.MAX_VALUE) {
			throw new IllegalStateException(path + " " + key + " must be a non-negative integer.");
		}
		return (int) value;
	}

	private static double readOpacity(JsonObject json, String key, Path path) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalStateException(path + " " + key + " must be a number from 0 to 1.");
		}
		double value = element.getAsDouble();
		if (value < 0.0 || value > 1.0) {
			throw new IllegalStateException(path + " " + key + " must be from 0 to 1.");
		}
		return value;
	}

	private static void writeJson(Path path, JsonObject json) throws IOException {
		Files.createDirectories(path.getParent());
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(json, writer);
		}
	}
}
