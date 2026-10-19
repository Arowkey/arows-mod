package com.arowkey.arowsmod.map;

import java.util.List;
import java.util.UUID;

public record SavedPolygon(UUID ownerId, String ownerName, String name, String worldId, String color, List<MapPoint> points) {
	/** Pl3xMap marker id. A rename changes this key, so the old marker has to be removed. */
	public String markerKey() {
		return ownerId + "_" + name;
	}
}
