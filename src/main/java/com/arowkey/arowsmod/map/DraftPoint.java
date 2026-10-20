package com.arowkey.arowsmod.map;

/** A point on an in-progress polygon. Y is the operator's feet block and is not written to polygons.json. */
public record DraftPoint(int x, int y, int z) {
	public MapPoint toMapPoint() {
		return new MapPoint(this.x, this.z);
	}
}
