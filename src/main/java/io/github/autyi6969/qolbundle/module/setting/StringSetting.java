package io.github.autyi6969.qolbundle.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.text.Text;

/** Free text, e.g. a world seed. Shown as a text box. */
public class StringSetting extends Setting<String> {
	private final int maxLength;

	public StringSetting(String id, String defaultValue, int maxLength) {
		super(id, defaultValue);
		this.maxLength = maxLength;
	}

	public int getMaxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String value) {
		if (value == null) {
			return getDefault();
		}
		return value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	@Override
	public Text getValueText() {
		return Text.literal(get());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
