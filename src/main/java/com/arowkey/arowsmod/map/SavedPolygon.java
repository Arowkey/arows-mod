package com.arowkey.arowsmod.map;

import java.util.List;
import java.util.UUID;

public record SavedPolygon(UUID ownerId, String ownerName, String name, String worldId, String color, List<MapPoint> points) {
	public String markerKey() {
		return ownerId + "_" + name;
	}
}
