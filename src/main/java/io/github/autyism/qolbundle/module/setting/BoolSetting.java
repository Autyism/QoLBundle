package io.github.autyism.qolbundle.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, boolean defaultValue) {
		super(id, defaultValue);
	}

	public void toggle() {
		set(!get());
	}

	@Override
	public Component getValueText() {
		return get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
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
