package com.example.waypoint;

/** Simple POJO for JSON persistence. */
public class Waypoint {
	public String name;
	public String dimensionId;
	public int x;
	public int y;
	public int z;

	public Waypoint() {
	}

	public Waypoint(String name, String dimensionId, int x, int y, int z) {
		this.name = name;
		this.dimensionId = dimensionId;
		this.x = x;
		this.y = y;
		this.z = z;
	}
}
