package io.github.autyism.qolbundle.input;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets modules take over what moving the mouse does while they are active:
 * the AFK clicker swallows it (view lock), Freecam turns its own camera instead of the player.
 * Fed by {@link io.github.autyism.qolbundle.mixin.EntityMixin}.
 */
public final class ViewHooks {
	@FunctionalInterface
	public interface LookInterceptor {
		/** @return true when the mouse movement was used up and the player must not turn */
		boolean interceptLook(double deltaX, double deltaY);
	}

	private static final List<LookInterceptor> INTERCEPTORS = new ArrayList<>();

	private ViewHooks() {
	}

	public static void register(LookInterceptor interceptor) {
		INTERCEPTORS.add(interceptor);
	}

	/** Registers an interceptor that is asked before all others (used by the self-test). */
	public static void registerFirst(LookInterceptor interceptor) {
		INTERCEPTORS.add(0, interceptor);
	}

	public static boolean interceptLook(double deltaX, double deltaY) {
		for (LookInterceptor interceptor : INTERCEPTORS) {
			if (interceptor.interceptLook(deltaX, deltaY)) {
				return true;
			}
		}
		return false;
	}
}
