package co.uk.mullak99.panorama;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import co.uk.mullak99.panorama.config.PanoramaConfig;
import co.uk.mullak99.panorama.resource.PanoramicScreenshots;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.util.Identifier;

public class Panorama implements ClientModInitializer {

	public static final String NAME = "Panorama";
	public static final String NAMESPACE = "panorama";
	public static final Identifier DEFAULT = id("default");
	public static final Logger LOGGER = LogManager.getLogger(NAME);

	@Override
	public void onInitializeClient() {
		PanoramaConfig.init();
		PanoramicScreenshots.registerKeyBinding();
	}

	public static Identifier id(String id) {
		return new Identifier(NAMESPACE, id);
	}

}
