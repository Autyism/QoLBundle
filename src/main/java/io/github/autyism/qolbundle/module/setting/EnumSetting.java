package io.github.autyism.qolbundle.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** A choice between the constants of an enum. Shown as a button that cycles through them. */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] values;

	public EnumSetting(String id, E defaultValue) {
		super(id, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public void cycle(boolean backwards) {
		int next = (get().ordinal() + (backwards ? values.length - 1 : 1)) % values.length;
		set(values[next]);
	}

	/** Lang key: qolbundle.option.[enum class name].[constant], all lower case. */
	public static Component nameOf(Enum<?> value) {
		return Component.translatable("qolbundle.option."
				+ value.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + "."
				+ value.name().toLowerCase(Locale.ROOT));
	}

	@Override
	public Component getValueText() {
		return nameOf(get());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get().name().toLowerCase(Locale.ROOT));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (!json.isJsonPrimitive()) {
			return;
		}
		String name = json.getAsString();
		for (E candidate : values) {
			if (candidate.name().equalsIgnoreCase(name)) {
				set(candidate);
				return;
			}
		}
	}
}
