package io.github.autyism.qolbundle.gui;

import io.github.autyism.qolbundle.config.ConfigManager;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/** The main settings screen: every module with its on/off switch and a button to its own settings. */
public class ModuleListScreen extends ScrollListScreen {
	public ModuleListScreen(@Nullable Screen parent) {
		super(Component.translatable("qolbundle.gui.title"), parent);
	}

	@Override
	protected void buildRows() {
		if (ModuleRegistry.all().isEmpty()) {
			addRow(24, Component.translatable("qolbundle.gui.no_modules"), null);
			return;
		}
		for (ModuleCategory category : ModuleCategory.values()) {
			boolean headerAdded = false;
			for (Module module : ModuleRegistry.all()) {
				if (module.getCategory() != category) {
					continue;
				}
				if (!headerAdded) {
					addHeader(category.getDisplayName());
					headerAdded = true;
				}
				addModuleRow(module);
			}
		}
	}

	private void addModuleRow(Module module) {
		Row row = addRow(26, module.getName(), module.getDescription());

		Button settings = row.add(Button.builder(Component.translatable("qolbundle.gui.settings"),
				button -> this.minecraft.setScreen(new ModuleSettingsScreen(this, module))).size(60, 20).build());
		settings.active = module.hasVisibleSettings();

		Button toggle = row.add(Button.builder(toggleText(module), button -> {
			module.setEnabled(!module.isEnabled());
			button.setMessage(toggleText(module));
			ConfigManager.save();
		}).size(40, 20).build());
		toggle.setTooltip(Tooltip.create(module.getDescription()));
	}

	/** Share code buttons next to "Done". */
	@Override
	protected void addFooter() {
		int y = this.height - 27;
		int left = this.width / 2 - 154;
		Tooltip tooltip = Tooltip.create(Component.translatable("qolbundle.gui.share.tooltip"));
		Button export = addRenderableWidget(Button.builder(Component.translatable("qolbundle.gui.export"), button -> {
			this.minecraft.keyboardHandler.setClipboard(ConfigManager.exportCode());
			button.setMessage(Component.translatable("qolbundle.gui.export.done"));
		}).bounds(left, y, 100, 20).build());
		export.setTooltip(tooltip);
		Button importButton = addRenderableWidget(Button.builder(Component.translatable("qolbundle.gui.import"), button -> {
			String clipboard = this.minecraft.keyboardHandler.getClipboard();
			JsonObject parsed = ConfigManager.parseCode(clipboard);
			if (parsed == null) {
				button.setMessage(Component.translatable("qolbundle.gui.import.invalid").withStyle(ChatFormatting.RED));
				return;
			}
			this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
				if (confirmed) {
					ConfigManager.importCode(clipboard);
				}
				this.minecraft.setScreen(this);
			}, Component.translatable("qolbundle.gui.import.title"),
					Component.translatable("qolbundle.gui.import.message", ConfigManager.countModules(parsed))));
		}).bounds(left + 104, y, 100, 20).build());
		importButton.setTooltip(tooltip);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(left + 208, y, 100, 20).build());
	}

	static Component toggleText(Module module) {
		return module.isEnabled()
				? CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN)
				: CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED);
	}

	@Override
	public void onClose() {
		ConfigManager.save();
		super.onClose();
	}
}
