package io.github.autyi6969.qolbundle.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.autyi6969.qolbundle.QoLBundleClient;
import io.github.autyi6969.qolbundle.hud.HudAnchor;
import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.setting.BoolSetting;
import io.github.autyi6969.qolbundle.module.setting.EnumSetting;
import io.github.autyi6969.qolbundle.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
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
 * {@link io.github.autyi6969.qolbundle.mixin.GameRendererMixin}, the camera is turned in
 * {@link io.github.autyi6969.qolbundle.mixin.CameraMixin}.
 */
public class RearMirrorModule extends Module {
	/** The kept picture, handed to the GUI as a texture. It only points at the buffer below and owns nothing. */
	private static final class Picture extends AbstractTexture {
		void point(@Nullable GpuTexture texture, @Nullable GpuTextureView view) {
			this.glTexture = texture;
			this.glTextureView = view;
		}

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
	private SimpleFramebuffer buffer;
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
	public Framebuffer getPicture() {
		return buffer;
	}

	public int getCaptures() {
		return captures;
	}

	/** Whether this frame gets a backwards view at all. */
	public boolean wantsPicture() {
		MinecraftClient client = MinecraftClient.getInstance();
		boolean wanted = client.world != null && client.player != null && !client.options.hudHidden && client.currentScreen == null;
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
		MinecraftClient client = MinecraftClient.getInstance();
		Framebuffer main = client.getFramebuffer();
		GpuTexture source = main.getColorAttachment();
		if (source == null) {
			return;
		}
		int width = main.textureWidth;
		int height = main.textureHeight;
		if (buffer == null) {
			buffer = new SimpleFramebuffer("QoL Bundle rear mirror", width, height, false);
		} else if (buffer.textureWidth != width || buffer.textureHeight != height) {
			buffer.resize(width, height);
		}
		if (picture == null) {
			picture = new Picture();
			client.getTextureManager().registerTexture(TEXTURE, picture);
		}
		picture.point(buffer.getColorAttachment(), buffer.getColorAttachmentView());
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, buffer.getColorAttachment(), 0, 0, 0, 0, 0, width, height);
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
				picture.point(null, null);
			}
			buffer.delete();
			buffer = null;
		}
	}

	@Override
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
		HudAnchor anchor = position.get();
		if (!hasPicture || buffer == null || layout.isBlocked(anchor)) {
			return;
		}
		int width = layout.getScreenWidth() * size.get() / 100;
		int height = width * buffer.textureHeight / buffer.textureWidth;
		int top = layout.reserve(anchor, height + 6);
		int left = layout.xFor(anchor, width + 4);
		context.fill(left, top, left + width + 4, top + height + 4, 0xFF202020);
		// The picture is stored bottom-up, hence v from 1 to 0. Flipped left-right it reads like a
		// real mirror: what is behind your left shoulder is on the left.
		float u1 = flip.get() ? 1F : 0F;
		context.drawTexturedQuad(TEXTURE, left + 2, top + 2, left + 2 + width, top + 2 + height, u1, 1F - u1, 1F, 0F);
	}
}
