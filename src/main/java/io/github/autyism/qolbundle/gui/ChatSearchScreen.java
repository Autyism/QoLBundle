package io.github.autyism.qolbundle.gui;

import io.github.autyism.qolbundle.modules.ChatEnhancementsModule;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Type a word, see every chat line of this session that contains it (newest at the bottom). */
public class ChatSearchScreen extends Screen {
	private static final int LIST_TOP = 62;
	private static final int FOOTER_HEIGHT = 34;
	private static final int LINE_HEIGHT = 10;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0A0;

	@Nullable
	private final Screen parent;
	private final ChatEnhancementsModule module;
	private final List<ChatEnhancementsModule.Line> results = new ArrayList<>();
	private EditBox queryField;
	private String query = "";
	/** Lines scrolled up from the bottom. */
	private int scroll;

	public ChatSearchScreen(@Nullable Screen parent, ChatEnhancementsModule module) {
		super(Component.translatable("qolbundle.module.chat_enhancements.search.title"));
		this.parent = parent;
		this.module = module;
	}

	public int getResultCount() {
		return results.size();
	}

	public void setQuery(String text) {
		if (queryField != null) {
			queryField.setValue(text);
		} else {
			query = text;
		}
		runSearch(text);
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(this.width - 40, 300);
		queryField = new EditBox(this.font, (this.width - fieldWidth) / 2, 28, fieldWidth, 18,
				Component.translatable("qolbundle.module.chat_enhancements.search.hint"));
		queryField.setMaxLength(100);
		queryField.setHint(Component.translatable("qolbundle.module.chat_enhancements.search.hint"));
		queryField.setValue(query);
		queryField.setResponder(this::runSearch);
		addRenderableWidget(queryField);
		setInitialFocus(queryField);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(this.width / 2 - 75, this.height - 27, 150, 20).build());
		runSearch(query);
	}

	private void runSearch(String text) {
		query = text;
		scroll = 0;
		results.clear();
		String needle = text.trim().toLowerCase(Locale.ROOT);
		for (ChatEnhancementsModule.Line line : module.getHistory()) {
			if (needle.isEmpty() || line.lowerCase().contains(needle)) {
				results.add(line);
			}
		}
	}

	private int visibleLines() {
		return Math.max(1, (this.height - FOOTER_HEIGHT - LIST_TOP) / LINE_HEIGHT);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int max = Math.max(0, results.size() - visibleLines());
		scroll = Math.max(0, Math.min(max, scroll + (int) Math.round(verticalAmount * 3)));
		return true;
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.extractRenderState(context, mouseX, mouseY, deltaTicks);
	*///?} else {
	public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
	//?}
		context.drawCenteredString(this.font, this.title, this.width / 2, 11, WHITE);

		Component count = module.getHistory().isEmpty()
				? Component.translatable("qolbundle.module.chat_enhancements.search.empty")
				: Component.translatable("qolbundle.module.chat_enhancements.search.count", results.size(), module.getHistory().size());
		context.drawCenteredString(this.font, count, this.width / 2, LIST_TOP - 11, GRAY);

		int left = 12;
		int maxWidth = this.width - 24;
		int visible = visibleLines();
		int last = results.size() - 1 - scroll;
		int first = Math.max(0, last - visible + 1);
		int y = LIST_TOP + 2;
		for (int i = first; i <= last; i++) {
			ChatEnhancementsModule.Line line = results.get(i);
			Component shown = Component.empty()
					.append(Component.literal(String.format(Locale.ROOT, "[%02d:%02d] ", line.time().getHour(), line.time().getMinute()))
							.withStyle(ChatFormatting.DARK_GRAY))
					.append(line.text());
			if (this.font.width(shown) > maxWidth) {
				// Too long for one row: show the plain text cut to fit.
				String cut = this.font.plainSubstrByWidth(shown.getString(), maxWidth - this.font.width("...")) + "...";
				context.drawString(this.font, cut, left, y, WHITE);
			} else {
				context.drawString(this.font, shown, left, y, WHITE);
			}
			y += LINE_HEIGHT;
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
