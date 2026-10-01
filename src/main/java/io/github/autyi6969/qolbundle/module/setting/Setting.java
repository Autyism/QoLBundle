package io.github.autyi6969.qolbundle.module.setting;

import com.google.gson.JsonElement;
import io.github.autyi6969.qolbundle.module.Module;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

/** One configurable value of a module. Knows how to save itself to / load itself from JSON. */
public abstract class Setting<T> {
	private final String id;
	private final T defaultValue;
	private T value;
	private Module owner;

	protected Setting(String id, T defaultValue) {
		this.id = id;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public final void attach(Module owner) {
		this.owner = owner;
	}

	public final String getId() {
		return id;
	}

	public final T get() {
		return value;
	}

	public void set(T value) {
		this.value = sanitize(value);
	}

	public final T getDefault() {
		return defaultValue;
	}

	public final void reset() {
		this.value = defaultValue;
	}

	/** Clamp / validate a value before it is stored. */
	protected T sanitize(T value) {
		return value == null ? defaultValue : value;
	}

	public String getTranslationKey() {
		return owner.getTranslationKey() + ".setting." + id;
	}

	public Text getName() {
		return Text.translatable(getTranslationKey());
	}

	/** Optional longer explanation; null when the lang file has no ".desc" entry for this setting. */
	public Text getDescription() {
		String key = getTranslationKey() + ".desc";
		return I18n.hasTranslation(key) ? Text.translatable(key) : null;
	}

	/** How the current value is shown on a button / slider. */
	public abstract Text getValueText();

	public abstract JsonElement toJson();

	/** Must not throw on bad input; keep the current value instead. */
	public abstract void fromJson(JsonElement json);
}
