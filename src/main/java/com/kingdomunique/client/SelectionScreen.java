package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuButton;
import com.kingdomunique.client.ui.KuTextPanel;
import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.network.SelectionChoicePayload;
import com.kingdomunique.network.SelectionResultPayload;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** B「覚醒の祭壇」: preview above the hand, select a card, then explicitly confirm. */
@Environment(EnvType.CLIENT)
public final class SelectionScreen extends Screen {
    private final ContentRegistry.Kind kind;
    private List<String> optionIds;
    private final List<KuButton> cards = new ArrayList<>();
    private final KuTextPanel details = new KuTextPanel();
    private int selectedIndex = -1;
    private int previewIndex = -1;
    private KuButton decideButton;
    private boolean processing;
    private long sentAt;
    private boolean enterHeld;
    private String message = "";

    public SelectionScreen(ContentRegistry.Kind kind, List<String> optionIds) {
        super(Text.literal(switch (kind) {
            case ABILITY -> "ユニークスキルの覚醒";
            case RELIC -> "王国の遺物の選択";
            case CURSE -> "禁断の呪物の選択";
        }));
        this.kind = kind;
        this.optionIds = validOptions(optionIds);
    }

    private List<String> validOptions(List<String> ids) {
        return ids == null ? List.of() : ids.stream()
                .filter(id -> id != null && ContentRegistry.kindOf(id) == kind).distinct().toList();
    }

    private boolean hasOptions() { return !optionIds.isEmpty(); }

    private int accent() {
        return switch (kind) {
            case ABILITY -> KuTheme.GOLD;
            case RELIC -> KuTheme.CYAN;
            case CURSE -> KuTheme.PURPLE;
        };
    }

    private int columns() { return Math.max(1, Math.min(optionIds.size(), (width - 16) / 98)); }
    private int rows() { return Math.max(1, (optionIds.size() + columns() - 1) / columns()); }
    private int cardsTop() { return height - 110 - (rows() - 1) * 42; }

    @Override
    protected void init() {
        int focusedCard = cards.indexOf(getFocused());
        boolean confirmFocused = decideButton != null && getFocused() == decideButton;
        cards.clear();
        int columns = columns();
        int cardWidth = (width - 24 - (columns - 1) * 8) / columns;
        for (int i = 0; i < optionIds.size(); i++) {
            int index = i;
            var info = UniqueLoreRegistry.getOrFallback(optionIds.get(i));
            int rowCount = Math.min(columns, optionIds.size() - i / columns * columns);
            int left = (width - (rowCount * (cardWidth + 8) - 8)) / 2;
            KuButton card = KuButton.themed(Text.literal(info.name()), button -> select(index))
                    .accent(accent()).card(info.icon(), Text.empty(), () -> selectedIndex == index)
                    .dimensions(left + i % columns * (cardWidth + 8), cardsTop() + i / columns * 42,
                            cardWidth, 36).build();
            cards.add(addDrawableChild(card));
        }
        int buttonWidth = Math.min(190, width * 2 / 5);
        decideButton = addDrawableChild(KuButton.themed(Text.empty(), button -> {
                    if (hasOptions()) decide(); else close();
                }).accent(accent()).primary()
                .dimensions(width - 12 - buttonWidth, height - 62, buttonWidth, 32).build());
        updateButtons();
        // Confirmation is never the initial focus on an irreversible choice.
        if (!cards.isEmpty()) {
            setInitialFocus(confirmFocused && selectedIndex >= 0 && !processing ? decideButton
                    : cards.get(Math.clamp(focusedCard, 0, cards.size() - 1)));
        } else setInitialFocus(decideButton);
    }

    private void updateButtons() {
        decideButton.setMessage(Text.literal(processing ? "確定中…" : !hasOptions() ? "閉じる"
                : selectedIndex < 0 ? "カードを選んでください" : "このカードで確定"));
        decideButton.active = !processing && (!hasOptions() || selectedIndex >= 0);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).active = !processing;
        }
    }

    private void select(int index) {
        if (processing || index < 0 || index >= optionIds.size()) return;
        selectedIndex = index;
        message = "";
        details.reset();
        updateButtons();
    }

    private void decide() {
        if (processing || selectedIndex < 0 || selectedIndex >= optionIds.size()) return;
        if (!ClientPlayNetworking.canSend(SelectionChoicePayload.ID)) {
            message = "サーバーに接続できません。接続を確認して再試行してください。";
            details.reset();
            return;
        }
        processing = true;
        sentAt = System.currentTimeMillis();
        message = "";
        updateButtons();
        ClientPlayNetworking.send(new SelectionChoicePayload(kind, optionIds.get(selectedIndex)));
    }

    public void handleResult(SelectionResultPayload result) {
        if (result.kind() != kind) return;
        processing = false;
        if (!result.accepted()) {
            optionIds = validOptions(result.optionIds());
            selectedIndex = -1;
            previewIndex = -1;
            message = result.message();
            details.reset();
            clearChildren();
            init();
            return;
        }
        if (client != null && client.player != null) {
            client.player.playSound(kind == ContentRegistry.Kind.CURSE
                    ? SoundEvents.ENTITY_WITHER_SPAWN : SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8F, 1.0F);
        }
        super.close();
    }

    @Override
    public void tick() {
        if (processing && System.currentTimeMillis() - sentAt >= 5000) {
            processing = false;
            message = "サーバーから応答がありません。もう一度確定してください。";
            details.reset();
            updateButtons();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() { return !processing && !hasOptions(); }

    @Override
    public void close() { if (shouldCloseOnEsc()) super.close(); }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, KuTheme.BACKDROP);
        KuTheme.drawPanel(context, 5, 5, width - 10, height - 10, accent());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, title, 14, 13, accent());
        context.drawTextWithShadow(textRenderer, "カードを1枚選び、下のボタンで確定", 14, 31, KuTheme.SECONDARY);
        int top = 47;
        int bottom = cardsTop() - 20;
        KuTheme.drawSlot(context, 12, top, width - 24, bottom - top);
        int preview = selectedIndex >= 0 ? selectedIndex : 0;
        // Hover/focus previews never change the selected card or the outgoing choice.
        if (!processing) {
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).isFocused()) preview = i;
            }
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).isMouseOver(mouseX, mouseY)) preview = i;
            }
        }
        if (previewIndex != preview) { previewIndex = preview; details.reset(); }
        int emblemWidth = Math.min(116, width / 3);
        int textX = 20 + emblemWidth;
        details.begin(textX, top + 8, width - textX - 22, Math.max(12, bottom - top - 16));
        if (!message.isBlank()) {
            details.paragraph(textRenderer, message, KuTheme.DRAWBACK);
            details.paragraph(textRenderer, hasOptions() ? "候補を選び直して確定してください。" : "閉じてから再度選択画面を開いてください。", KuTheme.BODY);
        }
        if (hasOptions()) {
            var info = UniqueLoreRegistry.getOrFallback(optionIds.get(preview));
            drawEmblem(context, info, 12 + emblemWidth / 2, (top + bottom) / 2,
                    Math.min(emblemWidth / 2 - 7, (bottom - top) / 2 - 6));
            String state = selectedIndex == preview ? "選択中" : "プレビュー（未確定）";
            details.paragraph(textRenderer, state + " ・ " + info.name(), accent());
            details.paragraph(textRenderer, "操作: " + info.triggerInfo(), KuTheme.SECONDARY);
            for (String line : info.descriptionLines()) details.paragraph(textRenderer, line, KuTheme.BODY);
        } else {
            details.paragraph(textRenderer, "このカテゴリの候補は残っていません。", KuTheme.SECONDARY);
        }
        details.render(context, textRenderer);
        context.drawTextWithShadow(textRenderer, details.hint().isEmpty() ? "候補から1枚を選択" : details.hint(),
                14, cardsTop() - 12, KuTheme.MUTED);
        for (int i = 0; i < cards.size(); i++) {
            KuButton card = cards.get(i);
            // Text is a second cue: selected and keyboard focus are not color-only states.
            context.drawTextWithShadow(textRenderer,
                    "候補 " + (i + 1) + (selectedIndex == i ? " ・選択中" : " ・未選択"),
                    card.getX() + 26, card.getY() + 19, selectedIndex == i ? accent() : KuTheme.MUTED);
        }
        String status = processing ? "サーバーの確定を待っています。" : !hasOptions() ? "候補なし ・閉じて戻れます"
                : selectedIndex < 0 ? "カードを1枚選んでください。"
                : "選択中: " + ContentRegistry.nameOf(optionIds.get(selectedIndex));
        int y = height - 62;
        for (var line : textRenderer.wrapLines(Text.literal(status), decideButton.getX() - 24)) {
            context.drawTextWithShadow(textRenderer, line, 14, y, KuTheme.PRIMARY);
            y += 12;
        }
        if (hasOptions()) context.drawTextWithShadow(textRenderer, "確定後は変更できません", 14, height - 28, KuTheme.SECONDARY);
        context.drawTextWithShadow(textRenderer, "←→/数字: 選択  ↑↓: 詳細  Tab: 移動  Enter: 決定", 14, height - 15, KuTheme.MUTED);
    }

    private void drawEmblem(DrawContext context, UniqueLoreRegistry.UniqueInfo info, int x, int y, int radius) {
        // A restrained seal frames the selected power without covering its prose.
        for (int i = 0; i < radius; i++) {
            int half = radius - i;
            int color = i == 0 || i == radius - 1 ? accent() : KuTheme.BORDER;
            context.fill(x - half, y - i, x - half + 1, y - i + 1, color);
            context.fill(x + half, y - i, x + half + 1, y - i + 1, color);
            context.fill(x - half, y + i, x - half + 1, y + i + 1, color);
            context.fill(x + half, y + i, x + half + 1, y + i + 1, color);
        }
        float scale = Math.max(1, Math.min(3, radius / 12.0F));
        context.getMatrices().push();
        context.getMatrices().translate(x - 8 * scale, y - 8 * scale, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawItem(info.icon(), 0, 0);
        context.getMatrices().pop();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (enterHeld) return true;
            enterHeld = true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!processing && hasOptions()) {
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                int index = keyCode - GLFW.GLFW_KEY_1;
                if (index < cards.size()) { select(index); setFocused(cards.get(index)); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                int index = selectedIndex < 0 ? 0
                        : Math.floorMod(selectedIndex + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1), cards.size());
                select(index);
                setFocused(cards.get(index));
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            details.scroll(keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1
                    : keyCode == GLFW.GLFW_KEY_PAGE_UP ? -5 : 5);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) { details.reset(); return true; }
        if (keyCode == GLFW.GLFW_KEY_END) { details.end(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) enterHeld = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0 && details.contains(mouseX, mouseY)) { details.scroll(vertical > 0 ? -2 : 2); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
