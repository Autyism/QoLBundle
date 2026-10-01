package io.github.autyi6969.qolbundle.gui;

import io.github.autyi6969.qolbundle.config.ConfigManager;
import io.github.autyi6969.qolbundle.module.Module;
import io.github.autyi6969.qolbundle.module.ModuleCategory;
import io.github.autyi6969.qolbundle.module.ModuleRegistry;
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
