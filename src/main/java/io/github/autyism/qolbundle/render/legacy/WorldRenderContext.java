//? if >=1.21.9 <1.21.11 {
/*package io.github.autyism.qolbundle.render.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/^*
 * 1.21.9-1.21.10: what the mod's world-drawing calls get, with the same calls as Fabric's context of this name.
 * Fabric API for 1.21.9 has no world-drawing events, so on these two versions the mod fires its own
 * ({@link WorldRenderEvents}), and one jar runs on both.
 ^/
public final class WorldRenderContext {
	private PoseStack matrices = new PoseStack();
	private SubmitNodeCollector commandQueue;

	void prepare(PoseStack matrices, SubmitNodeCollector commandQueue) {
		this.matrices = matrices;
		this.commandQueue = commandQueue;
	}

	/^* The pose stack the game draws this frame's world with. ^/
	public PoseStack matrices() {
		return matrices;
	}

	/^* Where things to draw this frame are handed in. ^/
	public SubmitNodeCollector commandQueue() {
		return commandQueue;
	}
}
*///?}
