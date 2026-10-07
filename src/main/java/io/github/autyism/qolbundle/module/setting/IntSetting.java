package io.github.autyism.qolbundle.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;

public class IntSetting extends Setting<Integer> {
	private final int min;
	private final int max;
	private final String suffix;

	public IntSetting(String id, int defaultValue, int min, int max) {
		this(id, defaultValue, min, max, "");
	}

	/** @param suffix appended to the number when shown, e.g. "%" */
	public IntSetting(String id, int defaultValue, int min, int max, String suffix) {
		super(id, defaultValue);
		this.min = min;
		this.max = max;
		this.suffix = suffix;
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	@Override
	protected Integer sanitize(Integer value) {
		if (value == null) {
			return getDefault();
		}
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public Component getValueText() {
		return Component.literal(get() + suffix);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsInt());
		}
	}
}
