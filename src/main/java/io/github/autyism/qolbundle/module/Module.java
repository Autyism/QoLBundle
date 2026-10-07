package io.github.autyism.qolbundle.module;

import io.github.autyism.qolbundle.hud.HudLayout;
import io.github.autyism.qolbundle.module.setting.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

	public Component getName() {
		return Component.translatable(getTranslationKey() + ".name");
	}

	public Component getDescription() {
		return Component.translatable(getTranslationKey() + ".desc");
	}

	/** Registers a setting on this module. Call from the subclass field initializers / constructor. */
	protected final <S extends Setting<?>> S add(S setting) {
		setting.attach(this);
		settings.add(setting);
		return setting;
	}

	/** True when {@link #createCustomScreen} returns a screen. */
	public boolean hasCustomScreen() {
		return false;
	}

	/**
	 * A screen of the module's own (for things that do not fit the plain list of settings).
	 * It gets a button on the module's settings page; the label is the lang key ".custom_screen".
	 */
	public Screen createCustomScreen(Screen parent) {
		return null;
	}

	/** True when the settings page has something to show. */
	public final boolean hasVisibleSettings() {
		if (hasCustomScreen()) {
			return true;
		}
		for (Setting<?> setting : settings) {
			if (!setting.isHidden()) {
				return true;
			}
		}
		return false;
	}

	/** Called when the switch is flipped (also when the config file changes the state at startup). */
	protected void onEnabledChanged(boolean enabled) {
	}

	/** Called at the very start of each client tick, before the game looks at the keys. */
	public void onStartTick(Minecraft client) {
	}

	/** Called once per client tick. {@code client.player} and {@code client.world} may be null. */
	public void onTick(Minecraft client) {
	}

	/**
	 * Called every frame while the world is drawn. Draw with {@code GizmoDrawing} (lines, faces,
	 * boxes); whatever is added here is shown for this one frame.
	 */
	public void onRenderWorld(WorldRenderContext context) {
	}

	/**
	 * Called every frame just before the game hands this frame's entities to the renderer. For
	 * things that need real textured geometry (a see-through block): submit them through
	 * {@code context.commandQueue()}. Lines and boxes belong in {@link #onRenderWorld}.
	 */
	public void onSubmitWorld(WorldRenderContext context) {
	}

	/** Called every frame while the HUD is visible and the player is in a world. */
	public void onRenderHud(GuiGraphics context, DeltaTracker tickCounter, HudLayout layout) {
	}
}
