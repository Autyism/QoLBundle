package io.github.autyism.qolbundle.gui;

import io.github.autyism.qolbundle.config.ConfigManager;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.ModuleRegistry;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jspecify.annotations.Nullable;

/** The main settings screen: every module with its on/off switch and a button to its own settings. */
public class ModuleListScreen extends ScrollListScreen {
	public ModuleListScreen(@Nullable Screen parent) {
		super(Text.translatable("qolbundle.gui.title"), parent);
	}

	@Override
	protected void buildRows() {
		if (ModuleRegistry.all().isEmpty()) {
			addRow(24, Text.translatable("qolbundle.gui.no_modules"), null);
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

		ButtonWidget settings = row.add(ButtonWidget.builder(Text.translatable("qolbundle.gui.settings"),
				button -> this.client.setScreen(new ModuleSettingsScreen(this, module))).size(60, 20).build());
		settings.active = module.hasVisibleSettings();

		ButtonWidget toggle = row.add(ButtonWidget.builder(toggleText(module), button -> {
			module.setEnabled(!module.isEnabled());
			button.setMessage(toggleText(module));
			ConfigManager.save();
		}).size(40, 20).build());
		toggle.setTooltip(Tooltip.of(module.getDescription()));
	}

	/** Share code buttons next to "Done". */
	@Override
	protected void addFooter() {
		int y = this.height - 27;
		int left = this.width / 2 - 154;
		Tooltip tooltip = Tooltip.of(Text.translatable("qolbundle.gui.share.tooltip"));
		ButtonWidget export = addDrawableChild(ButtonWidget.builder(Text.translatable("qolbundle.gui.export"), button -> {
			this.client.keyboard.setClipboard(ConfigManager.exportCode());
			button.setMessage(Text.translatable("qolbundle.gui.export.done"));
		}).dimensions(left, y, 100, 20).build());
		export.setTooltip(tooltip);
		ButtonWidget importButton = addDrawableChild(ButtonWidget.builder(Text.translatable("qolbundle.gui.import"), button -> {
			String clipboard = this.client.keyboard.getClipboard();
			JsonObject parsed = ConfigManager.parseCode(clipboard);
			if (parsed == null) {
				button.setMessage(Text.translatable("qolbundle.gui.import.invalid").formatted(Formatting.RED));
				return;
			}
			this.client.setScreen(new ConfirmScreen(confirmed -> {
				if (confirmed) {
					ConfigManager.importCode(clipboard);
				}
				this.client.setScreen(this);
			}, Text.translatable("qolbundle.gui.import.title"),
					Text.translatable("qolbundle.gui.import.message", ConfigManager.countModules(parsed))));
		}).dimensions(left + 104, y, 100, 20).build());
		importButton.setTooltip(tooltip);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(left + 208, y, 100, 20).build());
	}

	static Text toggleText(Module module) {
		return module.isEnabled()
				? ScreenTexts.ON.copy().formatted(Formatting.GREEN)
				: ScreenTexts.OFF.copy().formatted(Formatting.RED);
	}

	@Override
	public void close() {
		ConfigManager.save();
		super.close();
	}
}
