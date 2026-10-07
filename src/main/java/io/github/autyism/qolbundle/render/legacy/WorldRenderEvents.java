//? if >=1.21.9 <1.21.11 {
/*package io.github.autyism.qolbundle.render.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;

/^*
 * 1.21.9-1.21.10: the two world-drawing moments the mod uses, fired by its own mixins at the same places as
 * Fabric's events of the same names on 1.21.10 (Fabric API for 1.21.9 has none).
 ^/
public final class WorldRenderEvents {
	/^* Just before the game hands in its entities ({@link io.github.autyism.qolbundle.mixin.WorldRenderEventsMixin}). ^/
	public static final Event BEFORE_ENTITIES = new Event();
	/^* Just before the game draws its debug shapes ({@link io.github.autyism.qolbundle.mixin.DebugRendererMixin}). ^/
	public static final Event BEFORE_DEBUG_RENDER = new Event();

	private static final WorldRenderContext CONTEXT = new WorldRenderContext();

	private WorldRenderEvents() {
	}

	public static void fireBeforeEntities(PoseStack matrices, SubmitNodeCollector commandQueue) {
		CONTEXT.prepare(matrices, commandQueue);
		BEFORE_ENTITIES.fire(CONTEXT);
	}

	public static void fireBeforeDebugRender() {
		BEFORE_DEBUG_RENDER.fire(CONTEXT);
	}

	public static final class Event {
		private final List<Consumer<WorldRenderContext>> listeners = new ArrayList<>();

		public void register(Consumer<WorldRenderContext> listener) {
			listeners.add(listener);
		}

		private void fire(WorldRenderContext context) {
			for (Consumer<WorldRenderContext> listener : listeners) {
				listener.accept(context);
			}
		}
	}
}
*///?}
