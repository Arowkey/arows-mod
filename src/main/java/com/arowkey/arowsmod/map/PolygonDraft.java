package com.arowkey.arowsmod.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PolygonDraft {
	private final UUID ownerId;
	private final String ownerName;
	private final String worldId;
	private String color;
	private final List<MapPoint> points = new ArrayList<>();

	public PolygonDraft(UUID ownerId, String ownerName, String worldId, String color) {
		this.ownerId = ownerId;
		this.ownerName = ownerName;
		this.worldId = worldId;
		this.color = color;
	}

	public UUID ownerId() {
		return this.ownerId;
	}

	public String ownerName() {
		return this.ownerName;
	}

	public String worldId() {
		return this.worldId;
	}

	public String color() {
		return this.color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public List<MapPoint> points() {
		return List.copyOf(this.points);
	}

	public void add(MapPoint point) {
		this.points.add(point);
	}

	public int size() {
		return this.points.size();
	}
}
