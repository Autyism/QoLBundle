package io.github.autyi6969.qolbundle.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.autyi6969.qolbundle.gui.ModuleListScreen;

/** Makes the "configure" button in Mod Menu open our settings screen. Only loaded when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModuleListScreen::new;
	}
}
