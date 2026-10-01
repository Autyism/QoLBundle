package io.github.autyi6969.qolbundle.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, boolean defaultValue) {
		super(id, defaultValue);
	}

	public void toggle() {
		set(!get());
	}

	@Override
	public Text getValueText() {
		return get() ? ScreenTexts.ON : ScreenTexts.OFF;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			set(json.getAsBoolean());
		}
	}
}
