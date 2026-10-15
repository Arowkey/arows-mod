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

		Pl3xMap api = requirePl3xMap();
		ArowsConfig config = ArowsConfig.load();
		PolygonStore store = new PolygonStore();
		store.load();
		Pl3xMapBridge bridge = new Pl3xMapBridge(store, config);
		bridge.register(api);
		MapPolygonCommands.register(config, store, bridge);
		LOGGER.info("ArowsMod map polygons are ready.");
	}

	public static Pl3xMap requirePl3xMap() {
		if (!FabricLoader.getInstance().isModLoaded("pl3xmap") || Pl3xMap.api() == null) {
			throw new IllegalStateException(
					"ArowsMod requires Pl3xMap on the Fabric server. Refusing to start because polygon markers would never be registered."
			);
		}
		return Pl3xMap.api();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
