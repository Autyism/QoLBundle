package io.github.autyism.qolbundle.gui;

import io.github.autyism.qolbundle.config.ConfigManager;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.DoubleSetting;
import io.github.autyism.qolbundle.module.setting.EnumSetting;
import io.github.autyism.qolbundle.module.setting.IntSetting;
import io.github.autyism.qolbundle.module.setting.Setting;
import io.github.autyism.qolbundle.module.setting.StringSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** Settings of one module: its switch on the first row, then one row per setting. */
public class ModuleSettingsScreen extends ScrollListScreen {
	private static final int WIDGET_WIDTH = 150;
	private final Module module;

	public ModuleSettingsScreen(Screen parent, Module module) {
		super(module.getName(), parent);
		this.module = module;
	}

	public Module getModule() {
		return module;
	}

	@Override
	protected void buildRows() {
		Row enabledRow = addRow(26, Text.translatable("qolbundle.gui.enabled"), module.getDescription());
		ButtonWidget toggle = enabledRow.add(ButtonWidget.builder(ModuleListScreen.toggleText(module), button -> {
			module.setEnabled(!module.isEnabled());
			button.setMessage(ModuleListScreen.toggleText(module));
		}).size(WIDGET_WIDTH, 20).build());
		toggle.setTooltip(Tooltip.of(module.getDescription()));

		if (module.hasCustomScreen()) {
			Row row = addRow(24, Text.translatable(module.getTranslationKey() + ".custom_screen"), null);
			row.add(ButtonWidget.builder(Text.translatable("qolbundle.gui.custom_screen"),
					button -> this.client.setScreen(module.createCustomScreen(this))).size(WIDGET_WIDTH, 20).build());
		}
		for (Setting<?> setting : module.getSettings()) {
			if (setting.isHidden()) {
				continue;
			}
			Row row = addRow(24, setting.getName(), null);
			ClickableWidget widget = row.add(createWidget(setting));
			Text description = setting.getDescription();
			if (description != null) {
				widget.setTooltip(Tooltip.of(description));
			}
		}
	}

	private ClickableWidget createWidget(Setting<?> setting) {
		if (setting instanceof BoolSetting bool) {
			return ButtonWidget.builder(bool.getValueText(), button -> {
				bool.toggle();
				button.setMessage(bool.getValueText());
			}).size(WIDGET_WIDTH, 20).build();
		}
		if (setting instanceof EnumSetting<?> choice) {
			return ButtonWidget.builder(choice.getValueText(), button -> {
				choice.cycle(false);
				button.setMessage(choice.getValueText());
			}).size(WIDGET_WIDTH, 20).build();
		}
		if (setting instanceof IntSetting number) {
			double span = number.getMax() - number.getMin();
			return new SettingSlider(WIDGET_WIDTH, (number.get() - number.getMin()) / span,
					fraction -> number.set((int) Math.round(number.getMin() + fraction * span)), number::getValueText);
		}
		if (setting instanceof DoubleSetting number) {
			double span = number.getMax() - number.getMin();
			return new SettingSlider(WIDGET_WIDTH, (number.get() - number.getMin()) / span,
					fraction -> number.set(number.getMin() + fraction * span), number::getValueText);
		}
		if (setting instanceof StringSetting text) {
			TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, WIDGET_WIDTH, 20, text.getName());
			field.setMaxLength(text.getMaxLength());
			field.setText(text.get());
			field.setChangedListener(text::set);
			return field;
		}
		throw new IllegalStateException("No widget for setting type " + setting.getClass().getSimpleName());
	}

	@Override
	protected void addFooter() {
		int y = this.height - 27;
		addDrawableChild(ButtonWidget.builder(Text.translatable("qolbundle.gui.reset"), button -> {
			for (Setting<?> setting : module.getSettings()) {
				// Hidden settings hold data (saved layouts and the like), not preferences: leave them.
				if (!setting.isHidden()) {
					setting.reset();
				}
			}
			clearAndInit();
		}).dimensions(this.width / 2 - 154, y, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 + 4, y, 150, 20).build());
	}

	@Override
	public void close() {
		ConfigManager.save();
		super.close();
	}

	/** A slider over 0..1 that reports changes to a setting and shows the setting's own text. */
	private static class SettingSlider extends SliderWidget {
		private final DoubleConsumer onChange;
		private final Supplier<Text> text;

		SettingSlider(int width, double fraction, DoubleConsumer onChange, Supplier<Text> text) {
			super(0, 0, width, 20, text.get(), fraction);
			this.onChange = onChange;
			this.text = text;
		}

		@Override
		protected void updateMessage() {
			setMessage(text.get());
		}

		@Override
		protected void applyValue() {
			onChange.accept(this.value);
		}
	}
}
