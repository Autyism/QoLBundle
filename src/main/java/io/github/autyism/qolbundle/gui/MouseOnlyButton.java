package io.github.autyism.qolbundle.gui;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import org.jspecify.annotations.Nullable;

/**
 * A button for adding to one of the game's own screens (chat, inventory). It can be clicked, but
 * the arrow keys and Tab never move onto it. An ordinary button would take those keys away from
 * the screen it was added to: in the chat, "up" would jump to the button instead of recalling the
 * last message.
 */
public class MouseOnlyButton extends Button.Plain {
	// "Text" alone would mean ButtonWidget.Text in here, hence the full name.
	public MouseOnlyButton(int x, int y, int width, int height, net.minecraft.network.chat.Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	@Nullable
	public ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
		return null;
	}
}
