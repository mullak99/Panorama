package co.uk.mullak99.panorama.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

@Config(name = "panorama")
public class PanoramaConfig implements ConfigData {

	public boolean lockScreenshotPitch = true;
	public boolean lockScreenshotYaw = false;
	public boolean screenshotIndividually = true;
	public boolean takeScreenshotAsync = true;
	public double screenshotsCompletedDelay = 200.0D;
	public double screenshotDelay = 5.0D;
	public boolean resizeExportedImage = false;
	public int resizeImageRes = 1024;

	public static void init() {
		AutoConfig.register(PanoramaConfig.class, GsonConfigSerializer::new);
	}

	public static PanoramaConfig getInstance() {
		return AutoConfig.getConfigHolder(PanoramaConfig.class).getConfig();
	}

}
