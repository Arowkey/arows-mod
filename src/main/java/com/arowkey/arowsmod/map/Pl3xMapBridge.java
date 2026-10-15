package com.arowkey.arowsmod.map;

import com.arowkey.arowsmod.ArowsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventHandler;
import net.pl3x.map.core.event.EventListener;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polygon;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.world.World;

import java.util.ArrayList;
import java.util.List;

public final class Pl3xMapBridge implements EventListener {
	public static final String LAYER_KEY = "arowsmod";

	private final PolygonStore store;
	private final ArowsConfig config;

	public Pl3xMapBridge(PolygonStore store, ArowsConfig config) {
		this.store = store;
		this.config = config;
	}

	public void register(Pl3xMap api) {
		api.getEventRegistry().register(this);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> this.syncLoadedWorlds());
	}

	@EventHandler
	public void onWorldLoaded(WorldLoadedEvent event) {
		this.attach(event.getWorld());
	}

	public void syncLoadedWorlds() {
		for (World world : ArowsMod.requirePl3xMap().getWorldRegistry()) {
			this.attach(world);
		}
	}

	public boolean show(SavedPolygon polygon) {
		World world = ArowsMod.requirePl3xMap().getWorldRegistry().get(polygon.worldId());
		if (world == null) {
			return false;
		}
		SimpleLayer layer = this.layer(world);
		String key = polygon.markerKey();
		if (layer.hasMarker(key)) {
			layer.removeMarker(key);
		}
		layer.addMarker(this.marker(polygon));
		return true;
	}

	public void hide(SavedPolygon polygon) {
		World world = ArowsMod.requirePl3xMap().getWorldRegistry().get(polygon.worldId());
		if (world == null || !world.getLayerRegistry().has(LAYER_KEY)) {
			return;
		}
		Layer existing = world.getLayerRegistry().get(LAYER_KEY);
		if (existing instanceof SimpleLayer layer) {
			layer.removeMarker(polygon.markerKey());
		}
	}

	private void attach(World world) {
		SimpleLayer layer = this.layer(world);
		for (SavedPolygon polygon : this.store.byWorld(world.getKey())) {
			if (!layer.hasMarker(polygon.markerKey())) {
				layer.addMarker(this.marker(polygon));
			}
		}
	}

	private SimpleLayer layer(World world) {
		if (world.getLayerRegistry().has(LAYER_KEY)) {
			Layer existing = world.getLayerRegistry().get(LAYER_KEY);
			if (existing instanceof SimpleLayer layer) {
				return layer;
			}
			throw new IllegalStateException("Pl3xMap world " + world.getKey() + " already has a layer named " + LAYER_KEY + " that ArowsMod does not own.");
		}
		SimpleLayer layer = new SimpleLayer(LAYER_KEY, () -> "ArowsMod");
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
		marker.setOptions(Options.builder()
				.stroke(true)
				.strokeWeight(this.config.strokeWeight())
				.strokeColor(this.config.strokeColor(rgb))
				.fill(true)
				.fillColor(this.config.fillColor(rgb))
				.tooltipContent(polygon.name() + " (" + polygon.ownerName() + ")")
				.build());
		return marker;
	}
}
