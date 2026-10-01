package io.github.autyi6969.qolbundle.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The single list of all modules. Adding a module = one new class + one register(...) call. */
public final class ModuleRegistry {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleRegistry() {
	}

	public static <M extends Module> M register(M module) {
		if (get(module.getId()) != null) {
			throw new IllegalArgumentException("Duplicate module id: " + module.getId());
		}
		MODULES.add(module);
		return module;
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static Module get(String id) {
		for (Module module : MODULES) {
			if (module.getId().equals(id)) {
				return module;
			}
		}
		return null;
	}
}
