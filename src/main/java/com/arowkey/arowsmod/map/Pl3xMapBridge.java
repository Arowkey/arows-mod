package com.arowkey.arowsmod.map;

import com.arowkey.arowsmod.ArowsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventHandler;
import net.pl3x.map.core.event.EventListener;
import net.pl3x.map.core.event.server.Pl3xMapEnabledEvent;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polygon;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Fill;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Stroke;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Pl3xMapBridge implements EventListener {
	/** Stable Pl3xMap layer id. The visible name comes from config key layer-name. */
	public static final String LAYER_KEY = "arowsmod";

	private final PolygonStore store;
	private final ArowsConfig config;
	private boolean eventsRegistered;
	private boolean tickRegistered;
	private boolean syncQueued;

	public Pl3xMapBridge(PolygonStore store, ArowsConfig config) {
		this.store = store;
		this.config = config;
	}

	public void registerWhenPl3xMapStarts() {
		// This listener is registered from the main entrypoint, before Pl3xMap registers its own
		// SERVER_STARTED listener, so it runs first: api() exists, but enable() has not run.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> this.onServerStarted());
	}

	private void onServerStarted() {
		Pl3xMap api = ArowsMod.pl3xMapApi();
		if (!this.eventsRegistered) {
			api.getEventRegistry().register(this);
			this.eventsRegistered = true;
		}
		if (api.isEnabled()) {
			this.syncLoadedWorlds();
		}
	}

	@EventHandler
	public void onEnabled(Pl3xMapEnabledEvent event) {
		// enable() fires this event and only then calls loadWorlds(), so the world list is still empty.
		this.syncQueued = true;
		if (this.tickRegistered) {
			return;
		}
		this.tickRegistered = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!this.syncQueued) {
				return;
			}
			this.syncQueued = false;
			this.syncLoadedWorlds();
		});
	}

	@EventHandler
	public void onWorldLoaded(WorldLoadedEvent event) {
		this.attach(event.getWorld());
	}

	public void syncLoadedWorlds() {
		Set<String> loaded = new HashSet<>();
		for (World world : ArowsMod.pl3xMapApi().getWorldRegistry()) {
			loaded.add(world.getKey());
			this.attach(world);
		}
		Set<String> waiting = new LinkedHashSet<>();
		for (SavedPolygon polygon : this.store.all()) {
			if (!loaded.contains(polygon.worldId())) {
				waiting.add(polygon.worldId());
			}
		}
		for (String worldId : waiting) {
			ArowsMod.warn("World {} is not on Pl3xMap yet. Its polygons will be registered when that world loads.", worldId);
		}
	}

	public boolean show(SavedPolygon polygon) {
		World world = ArowsMod.pl3xMapApi().getWorldRegistry().get(polygon.worldId());
		if (world == null) {
			return false;
		}
		boolean created = !world.getLayerRegistry().has(LAYER_KEY);
		SimpleLayer layer = this.layer(world);
		String key = polygon.markerKey();
		if (layer.hasMarker(key)) {
			layer.removeMarker(key);
		}
		try {
			layer.addMarker(this.marker(polygon));
		} catch (RuntimeException exception) {
			if (created) {
				this.logLayer(world, layer);
			}
			throw exception;
		}
		if (created) {
			this.logLayer(world, layer);
		}
		return true;
	}

	public void renameMarker(SavedPolygon previous, SavedPolygon next) {
		// The marker id includes the name, so the old id is removed after the new marker is added.
		World world = ArowsMod.pl3xMapApi().getWorldRegistry().get(previous.worldId());
		if (world == null) {
			throw new IllegalStateException("Pl3xMap is not showing " + previous.worldId() + " yet.");
		}
		boolean created = !world.getLayerRegistry().has(LAYER_KEY);
		SimpleLayer layer = this.layer(world);
		String oldKey = previous.markerKey();
		String newKey = next.markerKey();
		try {
			if (oldKey.equals(newKey)) {
				if (layer.hasMarker(oldKey)) {
					layer.removeMarker(oldKey);
				}
				layer.addMarker(this.marker(next));
				return;
			}
			layer.addMarker(this.marker(next));
			try {
				if (layer.hasMarker(oldKey)) {
					layer.removeMarker(oldKey);
				}
			} catch (RuntimeException exception) {
				try {
					if (layer.hasMarker(newKey)) {
						layer.removeMarker(newKey);
					}
				} catch (RuntimeException rollback) {
					exception.addSuppressed(rollback);
				}
				throw exception;
			}
		} finally {
			if (created) {
				this.logLayer(world, layer);
			}
		}
	}

	public void hide(SavedPolygon polygon) {
		World world = ArowsMod.pl3xMapApi().getWorldRegistry().get(polygon.worldId());
		if (world == null || !world.getLayerRegistry().has(LAYER_KEY)) {
			return;
		}
		Layer existing = world.getLayerRegistry().get(LAYER_KEY);
		if (existing instanceof SimpleLayer layer) {
			layer.removeMarker(polygon.markerKey());
		}
	}

	private void attach(World world) {
		boolean created = !world.getLayerRegistry().has(LAYER_KEY);
		SimpleLayer layer = this.layer(world);
		for (SavedPolygon polygon : this.store.byWorld(world.getKey())) {
			if (layer.hasMarker(polygon.markerKey())) {
				continue;
			}
			try {
				layer.addMarker(this.marker(polygon));
			} catch (RuntimeException exception) {
				ArowsMod.warn("Could not register marker {} for {} in {}.", polygon.name(), polygon.ownerName(), world.getKey(), exception);
			}
		}
		if (created) {
			this.logLayer(world, layer);
		}
	}

	private void logLayer(World world, SimpleLayer layer) {
		ArowsMod.info("Registered map layer {} ({}) in {} with {} {}.",
				LAYER_KEY,
				this.config.layerName(),
				world.getKey(),
				layer.getMarkers().size(),
				layer.getMarkers().size() == 1 ? "marker" : "markers");
	}

	public void refreshLayerLabels() {
		for (World world : ArowsMod.pl3xMapApi().getWorldRegistry()) {
			if (!world.getLayerRegistry().has(LAYER_KEY)) {
				continue;
			}
			Layer existing = world.getLayerRegistry().get(LAYER_KEY);
			if (existing instanceof SimpleLayer layer) {
				layer.setLabel(this.config.layerName());
			}
		}
	}

	private SimpleLayer layer(World world) {
		if (world.getLayerRegistry().has(LAYER_KEY)) {
			Layer existing = world.getLayerRegistry().get(LAYER_KEY);
			if (existing instanceof SimpleLayer layer) {
				layer.setLabel(this.config.layerName());
				return layer;
			}
			ArowsMod.warn("Could not register map layer {} in {}. A different layer already uses that id.", LAYER_KEY, world.getKey());
			throw new IllegalStateException("Pl3xMap world " + world.getKey() + " already has a layer named " + LAYER_KEY + " that ArowsMod does not own.");
		}
		SimpleLayer layer = new SimpleLayer(LAYER_KEY, this.config::layerName);
		layer.setShowControls(true);
		layer.setDefaultHidden(false);
		layer.setPriority(40);
		layer.setZIndex(450);
		layer.setLiveUpdate(true);
		layer.setUpdateInterval(1);
		world.getLayerRegistry().register(layer);
		return layer;
	}

	private Polygon marker(SavedPolygon polygon) {
		List<Point> points = new ArrayList<>();
		for (MapPoint point : polygon.points()) {
			points.add(Point.of(point.x(), point.z()));
		}
		Point first = points.getFirst();
		Point last = points.getLast();
		if (first.x() != last.x() || first.z() != last.z()) {
			points.add(first);
		}
		Polygon marker = Marker.polygon(polygon.markerKey(), Polyline.of(polygon.markerKey() + "-outline", points));
		int rgb = PlayerColor.rgb(polygon.color());
		if (this.config.markerPane() != null) {
			marker.setPane(this.config.markerPane());
		}
		marker.setOptions(this.options(polygon, rgb));
		return marker;
	}

	private Options options(SavedPolygon polygon, int rgb) {
		// Null config values are commented-out options and are left at Pl3xMap's own default.
		Options.Builder builder = Options.builder()
				.strokeWeight(this.config.strokeWeight())
				.strokeColor(this.config.strokeColor(rgb))
				.fillColor(this.config.fillColor(rgb))
				.tooltipContent(this.tooltip(polygon));
		if (this.config.strokeEnabled() != null) {
			builder.stroke(this.config.strokeEnabled());
		}
		if (this.config.strokeLineCap() != null) {
			builder.strokeLineCapShape(Stroke.LineCapShape.valueOf(this.config.strokeLineCap()));
		}
		if (this.config.strokeLineJoin() != null) {
			builder.strokeLineJoinShape(Stroke.LineJoinShape.valueOf(this.config.strokeLineJoin()));
		}
		if (this.config.strokeDashPattern() != null) {
			builder.strokeDashPattern(this.config.strokeDashPattern());
		}
		if (this.config.strokeDashOffset() != null) {
			builder.strokeDashOffset(this.config.strokeDashOffset());
		}
		if (this.config.fillEnabled() != null) {
			builder.fill(this.config.fillEnabled());
		}
		if (this.config.fillType() != null) {
			builder.fillType(Fill.Type.valueOf(this.config.fillType()));
		}
		if (this.config.tooltipPane() != null) {
			builder.tooltipPane(this.config.tooltipPane());
		}
		if (this.config.tooltipOffsetX() != null) {
			builder.tooltipOffset(Point.of(this.config.tooltipOffsetX(), this.config.tooltipOffsetZ()));
		}
		if (this.config.tooltipDirection() != null) {
			builder.tooltipDirection(Tooltip.Direction.valueOf(this.config.tooltipDirection()));
		}
		if (this.config.tooltipPermanent() != null) {
			builder.tooltipPermanent(this.config.tooltipPermanent());
		}
		if (this.config.tooltipSticky() != null) {
			builder.tooltipSticky(this.config.tooltipSticky());
		}
		if (this.config.tooltipOpacity() != null) {
			builder.tooltipOpacity(this.config.tooltipOpacity());
		}
		if (this.config.popupContent() != null) {
			builder.popupContent(this.config.popupContent());
		}
		if (this.config.popupPane() != null) {
			builder.popupPane(this.config.popupPane());
		}
		if (this.config.popupOffsetX() != null) {
			builder.popupOffset(Point.of(this.config.popupOffsetX(), this.config.popupOffsetZ()));
		}
		if (this.config.popupMaxWidth() != null) {
			builder.popupMaxWidth(this.config.popupMaxWidth());
		}
		if (this.config.popupMinWidth() != null) {
			builder.popupMinWidth(this.config.popupMinWidth());
		}
		if (this.config.popupMaxHeight() != null) {
			builder.popupMaxHeight(this.config.popupMaxHeight());
		}
		if (this.config.popupAutoPan() != null) {
			builder.popupShouldAutoPan(this.config.popupAutoPan());
		}
		if (this.config.popupAutoPanPaddingX() != null) {
			builder.popupAutoPanPadding(Point.of(this.config.popupAutoPanPaddingX(), this.config.popupAutoPanPaddingZ()));
		}
		if (this.config.popupAutoPanPaddingTopLeftX() != null) {
			builder.popupAutoPanPaddingTopLeft(Point.of(this.config.popupAutoPanPaddingTopLeftX(), this.config.popupAutoPanPaddingTopLeftZ()));
		}
		if (this.config.popupAutoPanPaddingBottomRightX() != null) {
			builder.popupAutoPanPaddingBottomRight(Point.of(this.config.popupAutoPanPaddingBottomRightX(), this.config.popupAutoPanPaddingBottomRightZ()));
		}
		if (this.config.popupKeepInView() != null) {
			builder.popupShouldKeepInView(this.config.popupKeepInView());
		}
		if (this.config.popupCloseButton() != null) {
			builder.popupCloseButton(this.config.popupCloseButton());
		}
		if (this.config.popupAutoClose() != null) {
			builder.popupShouldAutoClose(this.config.popupAutoClose());
		}
		if (this.config.popupCloseOnEscapeKey() != null) {
			builder.popupShouldCloseOnEscapeKey(this.config.popupCloseOnEscapeKey());
		}
		if (this.config.popupCloseOnClick() != null) {
			builder.popupShouldCloseOnClick(this.config.popupCloseOnClick());
		}
		return builder.build();
	}

	/**
	 * Pl3xMap renders tooltip and popup content as HTML. A custom tooltip from the config is operator
	 * HTML. The default tooltip is built from the polygon and player names, which are escaped.
	 */
	private String tooltip(SavedPolygon polygon) {
		if (this.config.tooltipContent() != null) {
			return this.config.tooltipContent();
		}
		return htmlText(polygon.name()) + " (" + htmlText(polygon.ownerName()) + ")";
	}

	private static String htmlText(String text) {
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}
