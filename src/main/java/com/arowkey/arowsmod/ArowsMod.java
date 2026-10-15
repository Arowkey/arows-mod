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

		if (!FabricLoader.getInstance().isModLoaded("pl3xmap")) {
			throw new IllegalStateException("ArowsMod requires the Pl3xMap mod. It is not loaded, so polygon markers cannot be registered.");
		}

		ArowsConfig config = ArowsConfig.load();
		PolygonStore store = new PolygonStore();
		store.load();
		Pl3xMapBridge bridge = new Pl3xMapBridge(store, config);
		MapPolygonCommands.register(config, store, bridge);
		bridge.registerWhenPl3xMapStarts();
		LOGGER.info("ArowsMod map commands are ready. Map layers register after Pl3xMap enables.");
	}

	public static Pl3xMap pl3xMapApi() {
		Pl3xMap api = Pl3xMap.api();
		if (api == null) {
			throw new IllegalStateException("Pl3xMap is loaded, but its API is not initialized yet.");
		}
		return api;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
