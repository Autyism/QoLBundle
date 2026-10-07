package io.github.autyism.qolbundle.modules;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
//? if >=1.21.6
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.hud.HudAnchor;
import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * GREY ZONE: single-player and your own server only.
 *
 * <p>Rear-view mirror: a small picture of what is directly behind you. Every frame the world is
 * drawn a second time looking backwards, that picture is kept, and then the normal view is drawn;
 * the kept picture is shown on the HUD. Costs roughly half the frame rate while it is on.
 *
 * <p>It shows what turning round (or the game's own front view, F5) would show: no more than the
 * client already has. The second drawing is started in
 * {@link io.github.autyism.qolbundle.mixin.GameRendererMixin}, the camera is turned in
 * {@link io.github.autyism.qolbundle.mixin.CameraMixin}.
 */
public class RearMirrorModule extends Module {
	/** The kept picture, handed to the GUI as a texture. It only points at the buffer below and owns nothing. */
	private static final class Picture extends AbstractTexture {
		//? if >=1.21.6 {
		void point(@Nullable GpuTexture texture, @Nullable GpuTextureView view) {
			this.texture = texture;
			this.textureView = view;
		}
		//?} else {
		/*void point(@Nullable GpuTexture texture) {
			this.texture = texture;
		}
		*///?}

		@Override
		public void close() {
			// The framebuffer owns the texture.
		}
	}

	private static final Identifier TEXTURE = QoLBundleClient.id("rear_mirror");
	@Nullable
	private static RearMirrorModule instance;
	private static boolean drawingRear;

	private final EnumSetting<HudAnchor> position = add(new EnumSetting<>("position", HudAnchor.TOP_RIGHT));
	private final IntSetting size = add(new IntSetting("size", 28, 10, 50, "%"));
	private final BoolSetting flip = add(new BoolSetting("flip", true));

	@Nullable
	private TextureTarget buffer;
	/**
	 * Created on first use, not with the module: a texture object picks up its sampler from the
	 * renderer when it is made, and the renderer does not exist yet while mods are being loaded.
	 * (Made too early it has no sampler, and the mirror shows whatever texture was used last.)
	 */
	@Nullable
	private Picture picture;
	private boolean hasPicture;
	private int captures;

	public RearMirrorModule() {
		super("rear_mirror", ModuleCategory.GREY, false);
		instance = this;
	}

	/** The module while switched on, otherwise null. Used by the mixins. */
	@Nullable
	public static RearMirrorModule current() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** True while the backwards view is being drawn (the camera must look behind, no hand). */
	public static boolean isDrawingRear() {
		return drawingRear;
	}

	/** The kept picture itself (the self-test saves it to a file to look at). */
	@Nullable
	public RenderTarget getPicture() {
		return buffer;
	}

	public int getCaptures() {
		return captures;
	}

	/** Whether this frame gets a backwards view at all. */
	public boolean wantsPicture() {
		Minecraft client = Minecraft.getInstance();
		boolean wanted = client.level != null && client.player != null && !client.options.hideGui && client.screen == null;
		if (!wanted) {
			hasPicture = false;
		}
		return wanted;
	}

	public void beginRear() {
		drawingRear = true;
	}

	/** Keeps what was just drawn (the view backwards) before the normal view overwrites it. */
	public void keepPicture() {
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		GpuTexture source = main.getColorTexture();
		if (source == null) {
			return;
		}
		int width = main.width;
		int height = main.height;
		if (buffer == null) {
			//? if >=26.3 {
			/*buffer = new TextureTarget("QoL Bundle rear mirror", width, height, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, null);
			*///?} elif >=26.2 {
			/*buffer = new TextureTarget("QoL Bundle rear mirror", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
			*///?} else
			buffer = new TextureTarget("QoL Bundle rear mirror", width, height, false);
		} else if (buffer.width != width || buffer.height != height) {
			buffer.resize(width, height);
		}
		if (picture == null) {
			picture = new Picture();
			client.getTextureManager().register(TEXTURE, picture);
		}
		//? if >=1.21.6 {
		picture.point(buffer.getColorTexture(), buffer.getColorTextureView());
		//?} else
		/*picture.point(buffer.getColorTexture());*/
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, buffer.getColorTexture(), 0, 0, 0, 0, 0, width, height);
		hasPicture = true;
		captures++;
	}

	public void endRear() {
		drawingRear = false;
	}

	@Override
	protected void onEnabledChanged(boolean enabled) {
		hasPicture = false;
		if (!enabled && buffer != null) {
			if (picture != null) {
				//? if >=1.21.6 {
				picture.point(null, null);
				//?} else
				/*picture.point(null);*/
			}
			buffer.destroyBuffers();
			buffer = null;
		}
	}

	@Override
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
		HudAnchor anchor = position.get();
		if (!hasPicture || buffer == null || layout.isBlocked(anchor)) {
			return;
		}
		int width = layout.getScreenWidth() * size.get() / 100;
		int height = width * buffer.height / buffer.width;
		int top = layout.reserve(anchor, height + 6);
		int left = layout.xFor(anchor, width + 4);
		context.fill(left, top, left + width + 4, top + height + 4, 0xFF202020);
		// The picture is stored bottom-up, hence v from 1 to 0. Flipped left-right it reads like a
		// real mirror: what is behind your left shoulder is on the left.
		float u1 = flip.get() ? 1F : 0F;
		//? if >=1.21.6 {
		context.blit(TEXTURE, left + 2, top + 2, left + 2 + width, top + 2 + height, u1, 1F - u1, 1F, 0F);
		//?} else {
		/*// The same corners through the older call: a region of "minus one" texture size runs u (and v) backwards.
		context.blit(net.minecraft.client.renderer.RenderType::guiTextured, TEXTURE, left + 2, top + 2, u1, 1F, width, height,
				flip.get() ? -1 : 1, -1, 1, 1);
		*///?}
	}
}
