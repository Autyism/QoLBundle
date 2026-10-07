package io.github.autyism.qolbundle.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import net.minecraft.network.chat.Component;

public class DoubleSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String suffix;

	public DoubleSetting(String id, double defaultValue, double min, double max, double step, String suffix) {
		super(id, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.suffix = suffix;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	@Override
	protected Double sanitize(Double value) {
		if (value == null || value.isNaN()) {
			return getDefault();
		}
		double snapped = Math.round(value / step) * step;
		return Math.max(min, Math.min(max, snapped));
	}

	@Override
	public Component getValueText() {
		int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
		return Component.literal(String.format(Locale.ROOT, "%." + decimals + "f", get()) + suffix);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
