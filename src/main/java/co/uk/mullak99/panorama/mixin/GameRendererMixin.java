/*
 * This java file has been adapted from this file: https://github.com/liachmodded/runorama/blob/93316ed7df7140786092140b2d757af12a0ac039/src/main/java/com/github/liachmodded/runorama/mixin/GameRendererMixin.java
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package co.uk.mullak99.panorama.mixin;

import java.io.File;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Optional;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.datafixers.util.Pair;
import co.uk.mullak99.panorama.Panorama;
import co.uk.mullak99.panorama.config.PanoramaConfig;
import co.uk.mullak99.panorama.resource.PanoramicScreenshots;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.Direction;

// TODO: rewrite; i dont know what im doing!
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	private boolean renderingPanorama;

	@Shadow
	@Final
	private Camera camera;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V", shift = Shift.BEFORE))
	public void panorama$render(float delta, long startTime, boolean tick, CallbackInfo ci) {
		if (PanoramicScreenshots.timeSinceLastKeyPress >= 0.0D) {
			PanoramicScreenshots.timeSinceLastKeyPress -= delta;
		}
		if (PanoramicScreenshots.onShot != -1) {
			PanoramicScreenshots.time += delta;
		}
		if (PanoramicScreenshots.time > 375.0D) {
			if (!PanoramicScreenshots.startingRotation.isEmpty()) {
				client.player.setPitch(PanoramicScreenshots.startingRotation.get().getFirst());
				client.player.setYaw(PanoramicScreenshots.startingRotation.get().getSecond());
			}
			if (client.player != null) {
				client.player.sendMessage(Text.translatable("panorama.panoramic_screenshot.broke"), false);
			}
			PanoramicScreenshots.onShot = -1;
			PanoramicScreenshots.startingRotation = Optional.empty();
			PanoramicScreenshots.currentScreenshotPath = Optional.empty();
			PanoramicScreenshots.time = 0.0D;
			PanoramicScreenshots.timeSinceLastKeyPress = 10.0D;
			PanoramicScreenshots.needsScreenshot = false;
		}
		if (PanoramicScreenshots.needsScreenshot) {
			Panorama.LOGGER.info("Taking screenshot");
			PanoramicScreenshots.needsScreenshot = false;

			Path root = PanoramicScreenshots.getPanoramicScreenshotFolder();
			File file = root.resolve("panorama_" + PanoramicScreenshots.onShot + ".png").toFile();
			if (PanoramicScreenshots.currentScreenshotPath.isEmpty()) {
				PanoramicScreenshots.currentScreenshotPath = Optional.of(root);
			}
			File rootFile = root.toFile();
			if (!rootFile.exists()) {
				rootFile.mkdirs();
			}

			if (PanoramicScreenshots.startingRotation.isEmpty()) {
				PanoramicScreenshots.startingRotation = Optional.of(Pair.of(PanoramaConfig.getInstance().lockScreenshotPitch ? 0.0F : client.player.getPitch(), PanoramaConfig.getInstance().lockScreenshotYaw ? client.player.getHorizontalFacing() == Direction.NORTH ? 180 : client.player.getHorizontalFacing() == Direction.EAST ? -90 : client.player.getHorizontalFacing() == Direction.SOUTH ? 0 : 90 : client.player.getYaw()));
			}

			// setup
			boolean wasRenderingPanorama = renderingPanorama;
			boolean culledBefore = client.chunkCullingEnabled;
			client.chunkCullingEnabled = false;
			renderingPanorama = true;
			Framebuffer framebuffer = new SimpleFramebuffer(this.client.getWindow().getFramebufferWidth(), this.client.getWindow().getFramebufferHeight(), true, MinecraftClient.IS_SYSTEM_MAC);
			client.worldRenderer.reloadTransparencyPostProcessor();
			this.setBlockOutlineEnabled(false);
			this.setRenderHand(false);

			// take
			client.player.setPitch(PanoramicScreenshots.startingRotation.get().getFirst());
			client.player.setYaw(PanoramicScreenshots.startingRotation.get().getSecond());
			framebuffer.beginWrite(true);
			MatrixStack stack = new MatrixStack();
			stack.multiply(PanoramicScreenshots.ROTATIONS.get(PanoramicScreenshots.onShot));
			doRender(delta, startTime, stack);
			takeScreenshot(root, PanoramicScreenshots.onShot, framebuffer);

			// restore
			client.player.setPitch(PanoramicScreenshots.startingRotation.get().getFirst() + PanoramicScreenshots.PITCHES.get(PanoramicScreenshots.onShot));
			client.player.setYaw(PanoramicScreenshots.startingRotation.get().getSecond() + PanoramicScreenshots.YAWS.get(PanoramicScreenshots.onShot));
			renderingPanorama = wasRenderingPanorama;
			client.chunkCullingEnabled = culledBefore;
			this.setBlockOutlineEnabled(true);
			this.setRenderHand(true);
			client.worldRenderer.reloadTransparencyPostProcessor();
			framebuffer.delete();

			if (client.player != null && PanoramaConfig.getInstance().screenshotIndividually) {
				client.player.sendMessage(Text.translatable("panorama.panoramic_screenshot.taken", Text.literal(String.valueOf(PanoramicScreenshots.onShot)), Text.literal(file.getName()).formatted(Formatting.UNDERLINE).styled((style) -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())))), false);
			}

			if (PanoramicScreenshots.onShot == 5) {
				PanoramicScreenshots.onShot = -1;
				client.player.setPitch(PanoramicScreenshots.startingRotation.get().getFirst());
				client.player.setYaw(PanoramicScreenshots.startingRotation.get().getSecond());
				PanoramicScreenshots.startingRotation = Optional.empty();
				PanoramicScreenshots.currentScreenshotPath = Optional.empty();
				PanoramicScreenshots.time = 0.0D;
				PanoramicScreenshots.timeSinceLastKeyPress = PanoramaConfig.getInstance().screenshotsCompletedDelay;
				if (client.player != null) {
					client.player.sendMessage(Text.translatable("panorama.panoramic_screenshot.saved", Text.literal(root.toAbsolutePath().toString()).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, root.toAbsolutePath().toString())).withUnderline(true))), false);
				}
			} else {
				// push
				client.player.setPitch(PanoramicScreenshots.startingRotation.get().getFirst() + PanoramicScreenshots.PITCHES.get(PanoramicScreenshots.onShot + 1));
				client.player.setYaw(PanoramicScreenshots.startingRotation.get().getSecond() + PanoramicScreenshots.YAWS.get(PanoramicScreenshots.onShot + 1));
				if (!PanoramaConfig.getInstance().screenshotIndividually) {
					PanoramicScreenshots.needsScreenshot = true;
					PanoramicScreenshots.onShot++;
					panorama$render(delta, startTime, tick, ci);
				}
			}
		}
	}

	@Unique
	private void doRender(float tickDelta, long startTime, MatrixStack matrixStack) {
		this.renderWorld(tickDelta, Util.getMeasuringTimeNano() + startTime, matrixStack);
	}

	@Unique
	private void takeScreenshot(Path folder, int id, Framebuffer buffer) {
		NativeImage shot = ScreenshotRecorder.takeScreenshot(buffer);
		if (PanoramaConfig.getInstance().takeScreenshotAsync) {
			PanoramicScreenshots.saveScreenshotAsync(shot, folder, id);
		} else {
			PanoramicScreenshots.saveScreenshot(shot, folder, id);
		}
	}

	@Shadow
	public abstract void renderWorld(float delta, long startTime, MatrixStack matrices);

	@Shadow
	public abstract void setBlockOutlineEnabled(boolean blockOutlineEnabled);

	@Shadow
	public abstract void setRenderHand(boolean renderHand);

}
