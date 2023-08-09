/*
 * This java file has been adapted from this file: https://github.com/liachmodded/runorama/blob/93316ed7df7140786092140b2d757af12a0ac039/src/code/java/com/github/liachmodded/runorama/Runorama.java
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package co.uk.mullak99.panorama.resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.joml.Quaternionf;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import co.uk.mullak99.panorama.Panorama;
import co.uk.mullak99.panorama.config.PanoramaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;

//TODO: rewrite; i dont know what im doing!
public class PanoramicScreenshots {

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

	public static final List<Quaternionf> ROTATIONS = ImmutableList.of(RotationAxis.POSITIVE_Y.rotationDegrees(0), RotationAxis.POSITIVE_Y.rotationDegrees(90), RotationAxis.POSITIVE_Y.rotationDegrees(180), RotationAxis.POSITIVE_Y.rotationDegrees(270), RotationAxis.POSITIVE_X.rotationDegrees(-90), RotationAxis.POSITIVE_X.rotationDegrees(90));

	public static final List<Float> PITCHES = ImmutableList.of(0.0F, 0.0F, 0.0F, 0.0F, 90.0F, -90.0F);
	public static final List<Float> YAWS = ImmutableList.of(0.0F, 90.0F, 180.0F, -90.0F, 0.0F, 0.0F);

	public static double time = 0.0D;
	public static double timeSinceLastKeyPress = -1.0D;
	public static boolean needsScreenshot = false;
	public static int onShot = -1;
	public static Optional<Pair<Float, Float>> startingRotation = Optional.empty();
	public static Optional<Path> currentScreenshotPath = Optional.empty();

	public static void registerKeyBinding() {
		KeyBinding screenshotKey = new KeyBinding("key.panorama.panoramic_screenshot", 'H', "key.categories.misc");
		KeyBindingHelper.registerKeyBinding(screenshotKey);
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.currentScreen == null)
			{
				if (screenshotKey.isPressed() && timeSinceLastKeyPress <= 0.0D) {
					needsScreenshot = true;
					onShot++;
					timeSinceLastKeyPress = PanoramaConfig.getInstance().screenshotDelay;
				}
				else if (!screenshotKey.isPressed() && timeSinceLastKeyPress > 0.0D) {
					timeSinceLastKeyPress = 0D;
				}
			}
		});
	}

	public static void saveScreenshotAsync(NativeImage screenshot, Path folder, int i) {
		CompletableFuture.runAsync(() -> {
			saveScreenshot(screenshot, folder, i);
		}, Util.getIoWorkerExecutor());
	}

	public static void saveScreenshot(NativeImage screenshot, Path folder, int i) {
		try {
			String imageName = "panorama_" + i + ".png";

			int width = screenshot.getWidth();
			int height = screenshot.getHeight();
			int x = 0;
			int y = 0;
			if (width > height) {
				x = (width - height) / 2;
				width = height;
			} else {
				y = (height - width) / 2;
				height = width;
			}
			NativeImage saved = new NativeImage(width, height, false);
			screenshot.resizeSubRectTo(x, y, width, height, saved);

			if (PanoramaConfig.getInstance().resizeExportedImage) {
				CompletableFuture<NativeImage> resizeTask = resizeNativeImageAsync(saved, PanoramaConfig.getInstance().resizeImageRes);

				resizeTask.thenAcceptAsync(resizedImage -> {
					try {
						resizedImage.writeTo(folder.resolve(imageName));
					} catch (IOException exception) {
						Panorama.LOGGER.warn("Couldn't save resized screenshot", exception);
					} finally {
						resizedImage.close();
						saved.close();
					}
				}, Util.getIoWorkerExecutor());
			} else {
				saved.writeTo(folder.resolve(imageName));
				saved.close();
			}
		} catch (IOException exception) {
			Panorama.LOGGER.warn("Couldn't save screenshot", exception);
		} finally {
			screenshot.close();
		}
	}

	public static CompletableFuture<NativeImage> resizeNativeImageAsync(NativeImage source, int targetRes) {
		Supplier<NativeImage> supplier = () -> {
			return resizeNativeImage(source, targetRes);
		};
		return CompletableFuture.supplyAsync(supplier);
	}

	public static NativeImage resizeNativeImage(NativeImage source, int targetRes) {
		if (targetRes < 1) {
			Panorama.LOGGER.warn("Invalid image resolution");
		} else {
			try {
				NativeImage resizedImage = new NativeImage(targetRes, targetRes, false);
	
				for (int y = 0; y < targetRes; y++) {
					for (int x = 0; x < targetRes; x++) {
						int sourceX = x * source.getWidth() / targetRes;
						int sourceY = y * source.getHeight() / targetRes;
						int pixel = source.getColor(sourceX, sourceY);
						resizedImage.setColor(x, y, pixel);
					}
				}
				return resizedImage;
			} catch (Exception e) {
				Panorama.LOGGER.warn("Cannot resize image!");
			}
		}
		return source;
	}

	public static Path getPanoramicScreenshotFolder() {
		if (currentScreenshotPath.isPresent()) {
			return currentScreenshotPath.get();
		}
		File rootFile = FabricLoader.getInstance().getGameDir().resolve("screenshots/panoramas/").toFile();
		if (!rootFile.exists()) {
			rootFile.mkdirs();
		}
		String string = DATE_FORMAT.format(new Date());
		int i = 1;

		while (true) {
			File file = new File(rootFile, string + (i == 1 ? "" : "_" + i));
			if (!file.exists()) {
				file.mkdir();
				return file.toPath();
			}

			++i;
		}
	}

}
