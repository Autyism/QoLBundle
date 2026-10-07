package io.github.autyism.qolbundle.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.autyism.qolbundle.QoLBundleClient;
import io.github.autyism.qolbundle.data.WorldData;
import io.github.autyism.qolbundle.gui.ChatSearchScreen;
import io.github.autyism.qolbundle.gui.MouseOnlyButton;
import io.github.autyism.qolbundle.mixin.ChatHudAccessor;
import io.github.autyism.qolbundle.module.Module;
import io.github.autyism.qolbundle.module.ModuleCategory;
import io.github.autyism.qolbundle.module.setting.BoolSetting;
import io.github.autyism.qolbundle.module.setting.StringSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Four small improvements to the chat:
 * a timestamp in front of every line, a mark + sound + pop-up when somebody mentions you,
 * a search over everything said this session, and the chat window surviving a disconnect
 * (the game normally empties it).
 */
public class ChatEnhancementsModule extends Module {
	private static final int MAX_HISTORY = 2000;
	/** A line is treated as "somebody said something" when one of these follows the speaker's name. */
	private static final String[] SPEAKER_SEPARATORS = {"> ", ": ", "» "};
	/** Only used when none of the above is found: "[Server] hello". Otherwise "]" just closes a rank tag. */
	private static final String BRACKET_SEPARATOR = "] ";
	private static final int SPEAKER_SEARCH_LIMIT = 48;

	@Nullable
	private static ChatEnhancementsModule instance;

	private final BoolSetting timestamps = add(new BoolSetting("timestamps", true));
	private final BoolSetting timestampSeconds = add(new BoolSetting("timestamp_seconds", false));
	private final BoolSetting mentionMark = add(new BoolSetting("mention_mark", true));
	private final BoolSetting mentionSound = add(new BoolSetting("mention_sound", true));
	private final BoolSetting mentionToast = add(new BoolSetting("mention_toast", true));
	private final StringSetting keywords = add(new StringSetting("keywords", "", 120));
	private final BoolSetting keepHistory = add(new BoolSetting("keep_history", true));
	private final BoolSetting searchButton = add(new BoolSetting("search_button", true));

	private final KeyMapping searchKey;

	/** One chat line as it arrived, for searching. */
	public record Line(LocalTime time, Component text, String lowerCase) {
	}

	/** What the chat window held when the player left a world. */
	private record SavedChat(List<GuiMessage> lines, List<String> sentHistory) {
	}

	/** Everything said, per world / server, for the search screen. */
	private final Map<String, List<Line>> history = new HashMap<>();
	private final Map<String, SavedChat> savedChats = new HashMap<>();
	/** The world the player is in, as last seen from the tick; survives the moment of disconnecting. */
	@Nullable
	private String currentWorldId;
	private boolean wasInWorld;
	/** True while this module itself puts lines into the chat window; those must not be decorated again. */
	private boolean addingOwnLine;
	private int mentionCount;
	private int restoredLines;

	public ChatEnhancementsModule() {
		super("chat_enhancements", ModuleCategory.TOOLS, true);
		instance = this;
		searchKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.qolbundle.chat_search",
				InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), QoLBundleClient.KEY_CATEGORY));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen && isEnabled() && searchButton.get()) {
				// Mouse only: the arrow keys and Tab belong to the chat box (message history, completion).
				Screens.getButtons(screen).add(new MouseOnlyButton(width - 64, height - 34, 60, 16,
						Component.translatable(getTranslationKey() + ".search.button"),
						button -> client.setScreen(new ChatSearchScreen(null, this))));
			}
		});
	}

	/** Lines of the current world, oldest first. */
	public List<Line> getHistory() {
		List<Line> lines = currentWorldId == null ? null : history.get(currentWorldId);
		return lines == null ? List.of() : lines;
	}

	public int getMentionCount() {
		return mentionCount;
	}

	/** How many lines were put back into the chat window at the last (re)join (for the self-test). */
	public int getRestoredLines() {
		return restoredLines;
	}

	/** Forgets everything remembered in memory (for the self-test). */
	public void forgetAll() {
		history.clear();
		savedChats.clear();
		mentionCount = 0;
		restoredLines = 0;
	}

	@Override
	public void onTick(Minecraft client) {
		String worldId = WorldData.getWorldId();
		boolean inWorld = worldId != null && client.player != null;
		if (inWorld) {
			currentWorldId = worldId;
			if (!wasInWorld) {
				restoreChat(client, worldId);
			}
		}
		wasInWorld = inWorld;

		while (searchKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new ChatSearchScreen(null, this));
			}
		}
	}

	// ---- every line on its way into the chat window ---------------------------------------------

	/** Called by the mixin for each line added to the chat window. Returns the line to show. */
	public static Component decorate(Component message) {
		ChatEnhancementsModule module = instance;
		if (module == null || !module.isEnabled() || module.addingOwnLine) {
			return message;
		}
		try {
			return module.process(message);
		} catch (RuntimeException e) {
			// Never let a decoration problem swallow a chat message.
			QoLBundleClient.LOGGER.error("Chat Enhancements failed on a message", e);
			return message;
		}
	}

	private Component process(Component message) {
		Minecraft client = Minecraft.getInstance();
		LocalTime now = LocalTime.now();
		String plain = message.getString();

		if (currentWorldId != null) {
			List<Line> lines = history.computeIfAbsent(currentWorldId, key -> new ArrayList<>());
			lines.add(new Line(now, message, plain.toLowerCase(Locale.ROOT)));
			if (lines.size() > MAX_HISTORY) {
				lines.remove(0);
			}
		}

		boolean mentioned = isMention(message, plain, client.getUser().getName());
		if (mentioned) {
			mentionCount++;
			if (mentionSound.get()) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
			}
			if (mentionToast.get()) {
				String shortened = plain.length() > 60 ? plain.substring(0, 57) + "..." : plain;
				SystemToast.add(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
						Component.translatable(getTranslationKey() + ".mention.toast"), Component.literal(shortened));
			}
		}

		boolean mark = mentioned && mentionMark.get();
		if (!timestamps.get() && !mark) {
			return message;
		}
		var decorated = Component.empty();
		if (timestamps.get()) {
			String stamp = timestampSeconds.get()
					? String.format(Locale.ROOT, "[%02d:%02d:%02d] ", now.getHour(), now.getMinute(), now.getSecond())
					: String.format(Locale.ROOT, "[%02d:%02d] ", now.getHour(), now.getMinute());
			decorated.append(Component.literal(stamp).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (mark) {
			decorated.append(Component.literal("[@] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
		return decorated.append(message);
	}

	/**
	 * Does this line mention the player (or one of the extra keywords), and was it said by somebody else?
	 * Works on the visible text, so it also covers servers whose plugins send chat as plain system messages.
	 */
	private boolean isMention(Component message, String plain, String ownName) {
		if (message.getContents() instanceof TranslatableContents content
				&& content.getKey().equals("commands.message.display.incoming")) {
			return true; // a private message to you
		}
		// Where the speaker's part ends: the first "> ", ": " or "» ". In "[Admin] Alex: hi" that is
		// after "Alex", so the rank tag in front does not hide who is talking.
		int bodyStart = -1;
		int searchEnd = Math.min(plain.length(), SPEAKER_SEARCH_LIMIT);
		for (String separator : SPEAKER_SEPARATORS) {
			int index = plain.indexOf(separator);
			if (index >= 0 && index < searchEnd && (bodyStart < 0 || index + separator.length() < bodyStart)) {
				bodyStart = index + separator.length();
			}
		}
		if (bodyStart < 0) {
			int index = plain.indexOf(BRACKET_SEPARATOR);
			if (index >= 0 && index < searchEnd) {
				bodyStart = index + BRACKET_SEPARATOR.length();
			}
		}
		if (bodyStart < 0) {
			return false; // "X joined the game", death messages, command output: not somebody talking
		}
		String speaker = plain.substring(0, bodyStart);
		String body = plain.substring(bodyStart);
		if (containsWord(speaker, ownName)) {
			return false; // your own message
		}
		if (containsWord(body, ownName)) {
			return true;
		}
		for (String keyword : keywords.get().split(",")) {
			String trimmed = keyword.trim();
			if (!trimmed.isEmpty() && containsWord(body, trimmed)) {
				return true;
			}
		}
		return false;
	}

	/** Case-insensitive, and "Alex" does not match inside "Alexander". */
	private static boolean containsWord(String text, String word) {
		String haystack = text.toLowerCase(Locale.ROOT);
		String needle = word.toLowerCase(Locale.ROOT);
		int from = 0;
		while (true) {
			int index = haystack.indexOf(needle, from);
			if (index < 0) {
				return false;
			}
			boolean startOk = index == 0 || !isNameChar(haystack.charAt(index - 1));
			int end = index + needle.length();
			boolean endOk = end >= haystack.length() || !isNameChar(haystack.charAt(end));
			if (startOk && endOk) {
				return true;
			}
			from = index + 1;
		}
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	// ---- keeping the chat across a disconnect ----------------------------------------------------

	/** Called by the mixin right before the chat window is emptied. */
	public static void beforeChatCleared(ChatComponent chatHud, boolean clearHistory) {
		ChatEnhancementsModule module = instance;
		// clearHistory is true when leaving a world, false for the player's own F3+D "clear chat".
		if (module == null || !module.isEnabled() || !clearHistory || module.currentWorldId == null) {
			return;
		}
		List<GuiMessage> lines = List.copyOf(((ChatHudAccessor) chatHud).qolbundle$getMessages());
		if (!lines.isEmpty() && module.keepHistory.get()) {
			module.savedChats.put(module.currentWorldId, new SavedChat(lines, List.copyOf(chatHud.getRecentChat())));
		}
		module.currentWorldId = null;
	}

	private void restoreChat(Minecraft client, String worldId) {
		restoredLines = 0;
		SavedChat saved = savedChats.remove(worldId);
		if (saved == null || !keepHistory.get()) {
			return;
		}
		ChatComponent chatHud = client.gui.getChat();
		// Newest first. Lines that arrived since joining stay on top, then a divider, then the old chat.
		List<GuiMessage> merged = new ArrayList<>(((ChatHudAccessor) chatHud).qolbundle$getMessages());
		merged.add(new GuiMessage(client.gui.getGuiTicks(),
				Component.translatable(getTranslationKey() + ".restored").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
				null, GuiMessageTag.system()));
		merged.addAll(saved.lines);
		List<String> sent = saved.sentHistory.isEmpty() ? List.copyOf(chatHud.getRecentChat()) : saved.sentHistory;
		addingOwnLine = true;
		try {
			chatHud.restoreState(new ChatComponent.State(merged, sent, List.of()));
		} finally {
			addingOwnLine = false;
		}
		restoredLines = saved.lines.size();
	}
}
