package io.github.autyi6969.qolbundle.module;

import io.github.autyi6969.qolbundle.hud.HudLayout;
import io.github.autyi6969.qolbundle.module.setting.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One independent feature. Every module has its own on/off switch and its own settings.
 * Hooks are only called while the module is enabled.
 */
public abstract class Module {
	private final String id;
	private final ModuleCategory category;
	private final boolean defaultEnabled;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;

	protected Module(String id, ModuleCategory category, boolean defaultEnabled) {
		this.id = id;
		this.category = category;
		this.defaultEnabled = defaultEnabled;
		this.enabled = defaultEnabled;
	}

	public final String getId() {
		return id;
	}

	public final ModuleCategory getCategory() {
		return category;
	}

	public final boolean isDefaultEnabled() {
		return defaultEnabled;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		onEnabledChanged(enabled);
	}

	public final List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public final Setting<?> getSetting(String settingId) {
		for (Setting<?> setting : settings) {
			if (setting.getId().equals(settingId)) {
				return setting;
			}
		}
		return null;
	}

	public String getTranslationKey() {
		return "qolbundle.module." + id;
	}

	public Text getName() {
		return Text.translatable(getTranslationKey() + ".name");
	}

	public Text getDescription() {
		return Text.translatable(getTranslationKey() + ".desc");
	}

	/** Registers a setting on this module. Call from the subclass field initializers / constructor. */
	protected final <S extends Setting<?>> S add(S setting) {
		setting.attach(this);
		settings.add(setting);
		return setting;
	}

	/** Called when the switch is flipped (also when the config file changes the state at startup). */
	protected void onEnabledChanged(boolean enabled) {
	}

	/** Called once per client tick. {@code client.player} and {@code client.world} may be null. */
	public void onTick(MinecraftClient client) {
	}

	/**
	 * Called every frame while the world is drawn. Draw with {@code GizmoDrawing} (lines, faces,
	 * boxes); whatever is added here is shown for this one frame.
	 */
	public void onRenderWorld(WorldRenderContext context) {
	}

	/** Called every frame while the HUD is visible and the player is in a world. */
	public void onRenderHud(DrawContext context, RenderTickCounter tickCounter, HudLayout layout) {
	}
}
