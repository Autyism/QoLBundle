package io.github.autyi6969.qolbundle.gui;

import io.github.autyi6969.qolbundle.modules.ChatEnhancementsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
	private TextFieldWidget queryField;
	private String query = "";
	/** Lines scrolled up from the bottom. */
	private int scroll;

	public ChatSearchScreen(@Nullable Screen parent, ChatEnhancementsModule module) {
		super(Text.translatable("qolbundle.module.chat_enhancements.search.title"));
		this.parent = parent;
		this.module = module;
	}

	public int getResultCount() {
		return results.size();
	}

	public void setQuery(String text) {
		if (queryField != null) {
			queryField.setText(text);
		} else {
			query = text;
		}
		runSearch(text);
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(this.width - 40, 300);
		queryField = new TextFieldWidget(this.textRenderer, (this.width - fieldWidth) / 2, 28, fieldWidth, 18,
				Text.translatable("qolbundle.module.chat_enhancements.search.hint"));
		queryField.setMaxLength(100);
		queryField.setPlaceholder(Text.translatable("qolbundle.module.chat_enhancements.search.hint"));
		queryField.setText(query);
		queryField.setChangedListener(this::runSearch);
		addDrawableChild(queryField);
		setInitialFocus(queryField);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 - 75, this.height - 27, 150, 20).build());
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
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		super.render(context, mouseX, mouseY, deltaTicks);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 11, WHITE);

		Text count = module.getHistory().isEmpty()
				? Text.translatable("qolbundle.module.chat_enhancements.search.empty")
				: Text.translatable("qolbundle.module.chat_enhancements.search.count", results.size(), module.getHistory().size());
		context.drawCenteredTextWithShadow(this.textRenderer, count, this.width / 2, LIST_TOP - 11, GRAY);

		int left = 12;
		int maxWidth = this.width - 24;
		int visible = visibleLines();
		int last = results.size() - 1 - scroll;
		int first = Math.max(0, last - visible + 1);
		int y = LIST_TOP + 2;
		for (int i = first; i <= last; i++) {
			ChatEnhancementsModule.Line line = results.get(i);
			Text shown = Text.empty()
					.append(Text.literal(String.format(Locale.ROOT, "[%02d:%02d] ", line.time().getHour(), line.time().getMinute()))
							.formatted(Formatting.DARK_GRAY))
					.append(line.text());
			if (this.textRenderer.getWidth(shown) > maxWidth) {
				// Too long for one row: show the plain text cut to fit.
				String cut = this.textRenderer.trimToWidth(shown.getString(), maxWidth - this.textRenderer.getWidth("...")) + "...";
				context.drawTextWithShadow(this.textRenderer, cut, left, y, WHITE);
			} else {
				context.drawTextWithShadow(this.textRenderer, shown, left, y, WHITE);
			}
			y += LINE_HEIGHT;
		}
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
