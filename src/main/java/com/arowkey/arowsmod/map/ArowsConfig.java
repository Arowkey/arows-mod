package com.arowkey.arowsmod.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class ArowsConfig {
	public static final String MAX_POLYGONS_KEY = "max-polygons-per-player";
	public static final String STROKE_WEIGHT_KEY = "stroke-weight";
	public static final String STROKE_OPACITY_KEY = "stroke-opacity";
	public static final String FILL_OPACITY_KEY = "fill-opacity";
	public static final String LAYER_NAME_KEY = "layer-name";
	public static final String DEFAULT_LAYER_NAME = "ArowsMod";

	public static final int DEFAULT_MAX_POLYGONS = 10;
	public static final int DEFAULT_STROKE_WEIGHT = 3;
	public static final double DEFAULT_STROKE_OPACITY = 1.0;
	public static final double DEFAULT_FILL_OPACITY = 0.35;

	private static final Set<String> LINE_CAPS = Set.of("BUTT", "ROUND", "SQUARE");
	private static final Set<String> LINE_JOINS = Set.of("MITER", "ROUND", "BEVEL");
	private static final Set<String> FILL_TYPES = Set.of("NONZERO", "EVENODD");
	private static final Set<String> TOOLTIP_DIRECTIONS = Set.of("RIGHT", "LEFT", "TOP", "BOTTOM", "CENTER", "AUTO");

	private static final String TEMPLATE = """
			// ArowsMod polygon marker options. Lines starting with // are ignored and stay inactive.
			// Player colors are not stored here. Set them with /arowsmod map polygon color.
			{
			  "layer-name": "ArowsMod",
			  "max-polygons-per-player": 10,
			  "stroke-enabled": true,
			  "stroke-weight": 3,
			  "stroke-opacity": 1.0,
			  // "stroke-color": stroke ARGB. Leave commented. The stroke uses the player's color and stroke-opacity. There is no global color.
			  "stroke-line-cap": "round",
			  "stroke-line-join": "round",
			  // "stroke-dash-pattern": dash pattern, such as "5, 10". Pl3xMap has no default. Leave commented for a solid stroke.
			  // "stroke-dash-offset": distance into the dash pattern. Pl3xMap has no default.
			  "fill-enabled": true,
			  "fill-opacity": 0.35,
			  "fill-type": "evenodd",
			  // "fill-color": fill ARGB. Leave commented. The fill uses the player's color and fill-opacity. There is no global color.
			  // "marker-pane": map pane for the polygon marker. Pl3xMap has no default.
			  // "tooltip-content": HTML tooltip used for every polygon. Leave commented to show each polygon's name and owner.
			  "tooltip-pane": "tooltipPane",
			  "tooltip-offset-x": 0,
			  "tooltip-offset-z": 0,
			  "tooltip-direction": "auto",
			  "tooltip-permanent": false,
			  "tooltip-sticky": false,
			  "tooltip-opacity": 0.9,
			  // "popup-content": HTML shown when a polygon is clicked. Leave commented to disable the popup. Pl3xMap has no default.
			  "popup-pane": "popupPane",
			  "popup-offset-x": 0,
			  "popup-offset-z": 7,
			  "popup-max-width": 300,
			  "popup-min-width": 50,
			  // "popup-max-height": scroll height of the popup. Pl3xMap has no default.
			  "popup-auto-pan": true,
			  "popup-auto-pan-padding-x": 5,
			  "popup-auto-pan-padding-z": 5,
			  // "popup-auto-pan-padding-top-left-x": top-left auto-pan margin. Pl3xMap has no default. Overrides popup-auto-pan-padding when set.
			  // "popup-auto-pan-padding-top-left-z": top-left auto-pan margin. Pl3xMap has no default. Overrides popup-auto-pan-padding when set.
			  // "popup-auto-pan-padding-bottom-right-x": bottom-right auto-pan margin. Pl3xMap has no default. Overrides popup-auto-pan-padding when set.
			  // "popup-auto-pan-padding-bottom-right-z": bottom-right auto-pan margin. Pl3xMap has no default. Overrides popup-auto-pan-padding when set.
			  "popup-keep-in-view": false,
			  "popup-close-button": true,
			  "popup-auto-close": true,
			  "popup-close-on-escape-key": true,
			  "popup-close-on-click": true
			}
			""";

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

	public String layerName() {
		return this.snapshot.layerName;
	}

	public Boolean strokeEnabled() {
		return this.snapshot.strokeEnabled;
	}

	public String strokeLineCap() {
		return this.snapshot.strokeLineCap;
	}

	public String strokeLineJoin() {
		return this.snapshot.strokeLineJoin;
	}

	public String strokeDashPattern() {
		return this.snapshot.strokeDashPattern;
	}

	public String strokeDashOffset() {
		return this.snapshot.strokeDashOffset;
	}

	public Boolean fillEnabled() {
		return this.snapshot.fillEnabled;
	}

	public String fillType() {
		return this.snapshot.fillType;
	}

	public String markerPane() {
		return this.snapshot.markerPane;
	}

	public String tooltipContent() {
		return this.snapshot.tooltipContent;
	}

	public String tooltipPane() {
		return this.snapshot.tooltipPane;
	}

	public Integer tooltipOffsetX() {
		return this.snapshot.tooltipOffsetX;
	}

	public Integer tooltipOffsetZ() {
		return this.snapshot.tooltipOffsetZ;
	}

	public String tooltipDirection() {
		return this.snapshot.tooltipDirection;
	}

	public Boolean tooltipPermanent() {
		return this.snapshot.tooltipPermanent;
	}

	public Boolean tooltipSticky() {
		return this.snapshot.tooltipSticky;
	}

	public Double tooltipOpacity() {
		return this.snapshot.tooltipOpacity;
	}

	public String popupContent() {
		return this.snapshot.popupContent;
	}

	public String popupPane() {
		return this.snapshot.popupPane;
	}

	public Integer popupOffsetX() {
		return this.snapshot.popupOffsetX;
	}

	public Integer popupOffsetZ() {
		return this.snapshot.popupOffsetZ;
	}

	public Integer popupMaxWidth() {
		return this.snapshot.popupMaxWidth;
	}

	public Integer popupMinWidth() {
		return this.snapshot.popupMinWidth;
	}

	public Integer popupMaxHeight() {
		return this.snapshot.popupMaxHeight;
	}

	public Boolean popupAutoPan() {
		return this.snapshot.popupAutoPan;
	}

	public Integer popupAutoPanPaddingX() {
		return this.snapshot.popupAutoPanPaddingX;
	}

	public Integer popupAutoPanPaddingZ() {
		return this.snapshot.popupAutoPanPaddingZ;
	}

	public Integer popupAutoPanPaddingTopLeftX() {
		return this.snapshot.popupAutoPanPaddingTopLeftX;
	}

	public Integer popupAutoPanPaddingTopLeftZ() {
		return this.snapshot.popupAutoPanPaddingTopLeftZ;
	}

	public Integer popupAutoPanPaddingBottomRightX() {
		return this.snapshot.popupAutoPanPaddingBottomRightX;
	}

	public Integer popupAutoPanPaddingBottomRightZ() {
		return this.snapshot.popupAutoPanPaddingBottomRightZ;
	}

	public Boolean popupKeepInView() {
		return this.snapshot.popupKeepInView;
	}

	public Boolean popupCloseButton() {
		return this.snapshot.popupCloseButton;
	}

	public Boolean popupAutoClose() {
		return this.snapshot.popupAutoClose;
	}

	public Boolean popupCloseOnEscapeKey() {
		return this.snapshot.popupCloseOnEscapeKey;
	}

	public Boolean popupCloseOnClick() {
		return this.snapshot.popupCloseOnClick;
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

	/**
	 * Creates {@code config/arowsmod.json} on first launch. An older file that has no {@code //} comments
	 * and no {@code stroke-enabled} key is rewritten once, keeping the four original numbers.
	 * A file that already has comments is never rewritten here.
	 */
	public static ArowsConfig load() {
		Path path = configPath("arowsmod.json");
		try {
			if (Files.notExists(path)) {
				writeTemplate(path, DEFAULT_MAX_POLYGONS, DEFAULT_STROKE_WEIGHT, DEFAULT_STROKE_OPACITY, DEFAULT_FILL_OPACITY);
			} else {
				String existing = Files.readString(path);
				if (!existing.contains("//") && !stripComments(existing).contains("\"stroke-enabled\"")) {
					JsonObject previous = parseObject(existing, path);
					writeTemplate(
							path,
							readNonNegativeInt(previous, MAX_POLYGONS_KEY, path),
							readNonNegativeInt(previous, STROKE_WEIGHT_KEY, path),
							readOpacity(previous, STROKE_OPACITY_KEY, path),
							readOpacity(previous, FILL_OPACITY_KEY, path)
					);
				} else if (!existing.contains("layer-name")) {
					int open = existing.indexOf('{');
					if (open >= 0) {
						String inserted = existing.substring(0, open + 1)
								+ "\n  \"layer-name\": \"ArowsMod\","
								+ existing.substring(open + 1);
						Files.writeString(path, inserted);
					}
				}
			}
			return new ArowsConfig(read(path));
		} catch (RuntimeException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read ArowsMod config at " + path, exception);
		}
	}

	/** Re-reads the file. Does not write it, so commented lines stay. A bad file leaves the current snapshot. */
	public String reload() {
		Path path = configPath("arowsmod.json");
		if (Files.notExists(path)) {
			return path + " is missing. Kept the current config.";
		}
		try {
			this.snapshot = read(path);
			return null;
		} catch (RuntimeException exception) {
			return exception.getMessage() + " Kept the current config.";
		} catch (Exception exception) {
			return "Could not read " + path + ": " + exception.getMessage() + " Kept the current config.";
		}
	}

	/**
	 * Keys that are missing after comment stripping stay null and are not applied to markers.
	 * The four original keys are required.
	 */
	private static Snapshot read(Path path) throws IOException {
		JsonObject json = parseObject(Files.readString(path), path);
		IntPair tooltipOffset = readOptionalPair(json, "tooltip-offset-x", "tooltip-offset-z", path);
		IntPair popupOffset = readOptionalPair(json, "popup-offset-x", "popup-offset-z", path);
		IntPair popupPadding = readOptionalPair(json, "popup-auto-pan-padding-x", "popup-auto-pan-padding-z", path);
		IntPair popupPaddingTopLeft = readOptionalPair(json, "popup-auto-pan-padding-top-left-x", "popup-auto-pan-padding-top-left-z", path);
		IntPair popupPaddingBottomRight = readOptionalPair(json, "popup-auto-pan-padding-bottom-right-x", "popup-auto-pan-padding-bottom-right-z", path);
		return new Snapshot(
				readNonNegativeInt(json, MAX_POLYGONS_KEY, path),
				readNonNegativeInt(json, STROKE_WEIGHT_KEY, path),
				readOpacity(json, STROKE_OPACITY_KEY, path),
				readOpacity(json, FILL_OPACITY_KEY, path),
				readOptionalBoolean(json, "stroke-enabled", path),
				readOptionalEnum(json, "stroke-line-cap", path, LINE_CAPS),
				readOptionalEnum(json, "stroke-line-join", path, LINE_JOINS),
				readOptionalString(json, "stroke-dash-pattern", path),
				readOptionalString(json, "stroke-dash-offset", path),
				readOptionalBoolean(json, "fill-enabled", path),
				readOptionalEnum(json, "fill-type", path, FILL_TYPES),
				readOptionalString(json, "marker-pane", path),
				readOptionalString(json, "tooltip-content", path),
				readOptionalString(json, "tooltip-pane", path),
				tooltipOffset.x,
				tooltipOffset.z,
				readOptionalEnum(json, "tooltip-direction", path, TOOLTIP_DIRECTIONS),
				readOptionalBoolean(json, "tooltip-permanent", path),
				readOptionalBoolean(json, "tooltip-sticky", path),
				readOptionalUnit(json, "tooltip-opacity", path),
				readOptionalString(json, "popup-content", path),
				readOptionalString(json, "popup-pane", path),
				popupOffset.x,
				popupOffset.z,
				readOptionalInt(json, "popup-max-width", path),
				readOptionalInt(json, "popup-min-width", path),
				readOptionalInt(json, "popup-max-height", path),
				readOptionalBoolean(json, "popup-auto-pan", path),
				popupPadding.x,
				popupPadding.z,
				popupPaddingTopLeft.x,
				popupPaddingTopLeft.z,
				popupPaddingBottomRight.x,
				popupPaddingBottomRight.z,
				readOptionalBoolean(json, "popup-keep-in-view", path),
				readOptionalBoolean(json, "popup-close-button", path),
				readOptionalBoolean(json, "popup-auto-close", path),
				readOptionalBoolean(json, "popup-close-on-escape-key", path),
				readOptionalBoolean(json, "popup-close-on-click", path),
				readLayerName(json, path)
		);
	}

	private record IntPair(Integer x, Integer z) {
	}

	private record Snapshot(
			int maxPolygonsPerPlayer,
			int strokeWeight,
			double strokeOpacity,
			double fillOpacity,
			Boolean strokeEnabled,
			String strokeLineCap,
			String strokeLineJoin,
			String strokeDashPattern,
			String strokeDashOffset,
			Boolean fillEnabled,
			String fillType,
			String markerPane,
			String tooltipContent,
			String tooltipPane,
			Integer tooltipOffsetX,
			Integer tooltipOffsetZ,
			String tooltipDirection,
			Boolean tooltipPermanent,
			Boolean tooltipSticky,
			Double tooltipOpacity,
			String popupContent,
			String popupPane,
			Integer popupOffsetX,
			Integer popupOffsetZ,
			Integer popupMaxWidth,
			Integer popupMinWidth,
			Integer popupMaxHeight,
			Boolean popupAutoPan,
			Integer popupAutoPanPaddingX,
			Integer popupAutoPanPaddingZ,
			Integer popupAutoPanPaddingTopLeftX,
			Integer popupAutoPanPaddingTopLeftZ,
			Integer popupAutoPanPaddingBottomRightX,
			Integer popupAutoPanPaddingBottomRightZ,
			Boolean popupKeepInView,
			Boolean popupCloseButton,
			Boolean popupAutoClose,
			Boolean popupCloseOnEscapeKey,
			Boolean popupCloseOnClick,
			String layerName
	) {
	}

	static Path configPath(String... parts) {
		Path root = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
		Path path = root;
		for (String part : parts) {
			if (part.isBlank() || part.equals(".") || part.equals("..") || part.indexOf('/') >= 0 || part.indexOf('\\') >= 0) {
				throw new IllegalStateException("Refusing a config path that leaves the config directory.");
			}
			path = path.resolve(part);
		}
		Path normalized = path.toAbsolutePath().normalize();
		if (!normalized.startsWith(root)) {
			throw new IllegalStateException("Refusing a config path that leaves the config directory.");
		}
		return normalized;
	}

	private static JsonObject parseObject(String text, Path path) {
		JsonElement parsed = JsonParser.parseString(stripComments(text));
		if (parsed == null || !parsed.isJsonObject()) {
			throw new IllegalStateException(path + " must be a JSON object.");
		}
		return parsed.getAsJsonObject();
	}

	/** Drops {@code //} and block comments that sit outside JSON strings. The file on disk is left unchanged. */
	static String stripComments(String text) {
		StringBuilder out = new StringBuilder(text.length());
		boolean inString = false;
		boolean escape = false;
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			if (inString) {
				out.append(current);
				if (escape) {
					escape = false;
				} else if (current == '\\') {
					escape = true;
				} else if (current == '"') {
					inString = false;
				}
				continue;
			}
			if (current == '"') {
				inString = true;
				out.append(current);
				continue;
			}
			if (current == '/' && index + 1 < text.length()) {
				char next = text.charAt(index + 1);
				if (next == '/') {
					index += 2;
					while (index < text.length() && text.charAt(index) != '\n') {
						index++;
					}
					index--;
					continue;
				}
				if (next == '*') {
					index += 2;
					while (index + 1 < text.length() && !(text.charAt(index) == '*' && text.charAt(index + 1) == '/')) {
						index++;
					}
					if (index + 1 >= text.length()) {
						break;
					}
					index++;
					continue;
				}
			}
			out.append(current);
		}
		return out.toString();
	}

	private static void writeTemplate(Path path, int maxPolygons, int strokeWeight, double strokeOpacity, double fillOpacity) throws IOException {
		String text = TEMPLATE
				.replace("\"max-polygons-per-player\": 10", "\"max-polygons-per-player\": " + maxPolygons)
				.replace("\"stroke-weight\": 3", "\"stroke-weight\": " + strokeWeight)
				.replace("\"stroke-opacity\": 1.0", "\"stroke-opacity\": " + strokeOpacity)
				.replace("\"fill-opacity\": 0.35", "\"fill-opacity\": " + fillOpacity);
		Files.createDirectories(path.getParent());
		Files.writeString(path, text);
	}

	private static String readLayerName(JsonObject json, Path path) {
		String name = readOptionalString(json, LAYER_NAME_KEY, path);
		if (name == null) {
			return DEFAULT_LAYER_NAME;
		}
		if (name.isBlank()) {
			throw new IllegalStateException(path + " " + LAYER_NAME_KEY + " must not be blank.");
		}
		return name;
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

	private static Integer readOptionalInt(JsonObject json, String key, Path path) {
		if (!json.has(key)) {
			return null;
		}
		return readNonNegativeInt(json, key, path);
	}

	private static Integer readOptionalInteger(JsonObject json, String key, Path path) {
		if (!json.has(key)) {
			return null;
		}
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalStateException(path + " " + key + " must be an integer.");
		}
		double value = element.getAsDouble();
		if (value != Math.rint(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
			throw new IllegalStateException(path + " " + key + " must be an integer.");
		}
		return (int) value;
	}

	private static IntPair readOptionalPair(JsonObject json, String xKey, String zKey, Path path) {
		Integer x = readOptionalInteger(json, xKey, path);
		Integer z = readOptionalInteger(json, zKey, path);
		if ((x == null) != (z == null)) {
			throw new IllegalStateException(path + " " + xKey + " and " + zKey + " must be set together.");
		}
		return new IntPair(x, z);
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

	private static Double readOptionalUnit(JsonObject json, String key, Path path) {
		if (!json.has(key)) {
			return null;
		}
		return readOpacity(json, key, path);
	}

	private static Boolean readOptionalBoolean(JsonObject json, String key, Path path) {
		if (!json.has(key)) {
			return null;
		}
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalStateException(path + " " + key + " must be true or false.");
		}
		return element.getAsBoolean();
	}

	private static String readOptionalString(JsonObject json, String key, Path path) {
		if (!json.has(key)) {
			return null;
		}
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new IllegalStateException(path + " " + key + " must be a string.");
		}
		return element.getAsString();
	}

	private static String readOptionalEnum(JsonObject json, String key, Path path, Set<String> allowed) {
		String value = readOptionalString(json, key, path);
		if (value == null) {
			return null;
		}
		String normalized = value.toUpperCase(Locale.ROOT);
		if (!allowed.contains(normalized)) {
			throw new IllegalStateException(path + " " + key + " must be one of " + allowed + ".");
		}
		return normalized;
	}
}
