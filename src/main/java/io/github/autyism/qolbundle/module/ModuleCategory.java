package io.github.autyism.qolbundle.module;

import net.minecraft.text.Text;

import java.util.Locale;

/** Groups modules in the settings screen. Mirrors the categories in docs/HANDOVER.md section 6.2. */
public enum ModuleCategory {
	TECHNICAL,
	INFO,
	TOOLS,
	/** Reading other players: only what is in plain sight, never through walls. */
	PVP,
	/** Automation and information advantages: fine alone or on your own server, risky on public ones. */
	GREY;

	public Text getDisplayName() {
		return Text.translatable("qolbundle.category." + name().toLowerCase(Locale.ROOT));
	}
}
