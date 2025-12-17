package com.example.waypoint;

import net.fabricmc.api.ModInitializer;

/**
 * Common entrypoint.
 *
 * This mod is primarily client-side, but having a safe common entrypoint keeps
 * the template structure intact and avoids loading client-only classes on a server.
 */
public class WaypointMod implements ModInitializer {
	public static final String MOD_ID = "waypointmod";

	@Override
	public void onInitialize() {
		// Intentionally empty.
	}
}
