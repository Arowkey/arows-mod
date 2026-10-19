package com.arowkey.arowsmod;

import com.arowkey.arowsmod.command.MapPolygonCommands;
import com.arowkey.arowsmod.map.ArowsConfig;
import com.arowkey.arowsmod.map.Pl3xMapBridge;
import com.arowkey.arowsmod.map.PolygonStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.pl3x.map.core.Pl3xMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArowsMod implements ModInitializer {
	public static final String MOD_ID = "arowsmod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
			return;
		}

		// Fabric already refuses to launch when the pl3xmap mod is missing. api() is still null
		// here because Pl3xMap's dedicated-server initializer has not constructed it yet.
		if (!FabricLoader.getInstance().isModLoaded("pl3xmap")) {
			error("Pl3xMap is not loaded. Polygon markers cannot be registered.");
			throw new IllegalStateException("ArowsMod requires the Pl3xMap mod. It is not loaded, so polygon markers cannot be registered.");
		}

		ArowsConfig config = ArowsConfig.load();
		PolygonStore store = new PolygonStore();
		store.load();
		info("Loaded {} {} and {} {}.",
				store.polygonCount(), store.polygonCount() == 1 ? "polygon" : "polygons",
				store.colorCount(), store.colorCount() == 1 ? "player color" : "player colors");
		Pl3xMapBridge bridge = new Pl3xMapBridge(store, config);
		MapPolygonCommands.register(config, store, bridge);
		// Subscribe before Pl3xMap's own SERVER_STARTED listener runs enable().
		bridge.registerWhenPl3xMapStarts();
		info("Map commands are ready.");
	}

	/** Called after Pl3xMap's constructor has published the API. Not used during onInitialize. */
	public static Pl3xMap pl3xMapApi() {
		Pl3xMap api = Pl3xMap.api();
		if (api == null) {
			warn("Pl3xMap API is not ready.");
			throw new IllegalStateException("Pl3xMap is loaded, but its API is not initialized yet.");
		}
		return api;
	}

	/** Info line. The prefix is part of the message, not only the logger name. */
	public static void info(String message, Object... arguments) {
		LOGGER.info(prefix(message), arguments);
	}

	/** Failure the server kept running through. */
	public static void warn(String message, Object... arguments) {
		LOGGER.warn(prefix(message), arguments);
	}

	/** Failure that stops ArowsMod from starting. */
	public static void error(String message, Object... arguments) {
		LOGGER.error(prefix(message), arguments);
	}

	private static String prefix(String message) {
		return "[ArowsMod] " + message;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
