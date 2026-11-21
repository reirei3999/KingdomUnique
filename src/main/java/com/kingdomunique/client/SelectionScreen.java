package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.client.ui.KuButton;
import com.kingdomunique.network.SelectionChoicePayload;
import com.kingdomunique.network.SelectionResultPayload;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class SelectionScreen extends Screen {
    private final ContentRegistry.Kind kind;
    private List<String> optionIds;
    private int selectedIndex = -1;
    private KuButton decideButton;
    private long openedAt;
    private boolean processing;
    private long sentAt;
    private boolean enterHeld;
    private String message = "";
    private int detailIndex = -1;
    private int detailScroll;
    private int detailMaxScroll;

    public SelectionScreen(ContentRegistry.Kind kind, List<String> optionIds) {
        super(Text.literal(titleOf(kind)));
        this.kind = kind;
        this.optionIds = validOptions(optionIds);
    }

    private static List<String> validOptions(List<String> ids) {
        return ids == null ? List.of() : ids.stream().filter(id -> !"none".equals(id)).toList();
    }

    private boolean hasOptions() {
        return !optionIds.isEmpty();
    }

    private static String titleOf(ContentRegistry.Kind kind) {
        return switch (kind) {
            case ABILITY -> "§6§l✦ ユニーク能力覚醒（1つだけ獲得・世界排他）";
            case RELIC -> "§b§l✦ 王国の遺物選択（1つだけ獲得・世界排他）";
            case CURSE -> "§5§l✦ 禁断の呪物選択（1つだけ獲得・世界排他）";
        };
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        detailIndex = -1;
        detailScroll = 0;
        detailMaxScroll = 0;
        int buttonWidth = Math.min(150, width - 30);
        decideButton = addDrawableChild(KuButton.themed(Text.literal(hasOptions() ? "§lこのカードで確定" : "閉じる"),
                button -> { if (hasOptions()) decide(); else close(); })
                .accent(KuTheme.GOLD).textColor(KuTheme.MERIT)
                .dimensions((width - buttonWidth) / 2, height - 26, buttonWidth, 20).build());
        updateButton();
    }

    private void updateButton() {
        decideButton.setMessage(Text.literal(processing ? "確定中…" : hasOptions() ? "§lこのカードで確定" : "閉じる"));
        decideButton.active = !processing && (!hasOptions() || selectedIndex >= 0);
    }

    private record Layout(int columns, int cardWidth, int cardHeight, int detailTop) {}

    private Layout layout() {
        int available = Math.max(1, width - 24);
        if (!hasOptions()) return new Layout(1, 1, 1, 44);
        int columns = Math.max(1, (available + 8) / 88);
        int rows = optionIds.size() > columns ? 2 : 1;
        columns = Math.min(optionIds.size(), Math.max(columns, (optionIds.size() + rows - 1) / rows));
        int cardWidth = Math.max(1, Math.min(120, (available - (columns - 1) * 8) / columns));
        int cardHeight = Math.max(32, Math.min(145, (height - 166) / rows - 8));
        return new Layout(columns, cardWidth, cardHeight, 44 + rows * (cardHeight + 8));
    }

    private int cardX(Layout layout, int index) {
        int rowCount = Math.min(layout.columns(), optionIds.size() - index / layout.columns() * layout.columns());
        return (width - rowCount * (layout.cardWidth() + 8) + 8) / 2
                + index % layout.columns() * (layout.cardWidth() + 8);
    }

    private float progress(int index) {
        return Math.clamp((System.currentTimeMillis() - openedAt - index * 40) / 250.0F, 0, 1);
    }

    private int cardY(Layout layout, int index) {
        float progress = progress(index);
        return 44 + index / layout.columns() * (layout.cardHeight() + 8)
                + Math.round(10 * (1 - progress) * (1 - progress));
    }

    private int hoveredCard(Layout layout, double mouseX, double mouseY) {
        for (int i = 0; i < optionIds.size(); i++) {
            int x = cardX(layout, i);
            int y = cardY(layout, i);
            if (progress(i) > 0 && mouseX >= x && mouseX < x + layout.cardWidth()
                    && mouseY >= y - 4 && mouseY < y + layout.cardHeight()) {
                return i;
            }
        }
        return -1;
    }

    private void select(int index) {
        if (!processing && index >= 0 && index < optionIds.size()) {
            selectedIndex = index;
            setFocused(null);
            updateButton();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!enterHeld) {
                enterHeld = true;
                if (hasOptions()) decide();
                else if (!processing) close();
            }
            return true;
        }
        if (processing) return true;
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9
                && keyCode - GLFW.GLFW_KEY_1 < optionIds.size()) {
            select(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (hasOptions() && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            select(selectedIndex < 0 ? (keyCode == GLFW.GLFW_KEY_LEFT ? optionIds.size() - 1 : 0)
                    : Math.floorMod(selectedIndex + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1), optionIds.size()));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int amount = keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN
                    ? Math.max(20, height - 97 - layout().detailTop()) : 20;
            int direction = keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
            detailScroll = Math.clamp(detailScroll + direction * amount, 0, detailMaxScroll);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            enterHeld = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void decide() {
        if (processing || selectedIndex < 0 || selectedIndex >= optionIds.size()) {
            return;
        }
        String chosen = optionIds.get(selectedIndex);
        processing = true;
        sentAt = System.currentTimeMillis();
        message = "";
        updateButton();
        ClientPlayNetworking.send(new SelectionChoicePayload(kind, chosen));
    }

    public void handleResult(SelectionResultPayload result) {
        if (result.kind() != kind) return;
        processing = false;
        if (!result.accepted()) {
            optionIds = validOptions(result.optionIds());
            selectedIndex = -1;
            message = result.message();
            clearChildren();
            init();
            return;
        }
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.playSound(
                    kind == ContentRegistry.Kind.CURSE
                            ? SoundEvents.ENTITY_WITHER_SPAWN : SoundEvents.ENTITY_PLAYER_LEVELUP,
                    0.8F, 1.0F);
        }
        super.close();
    }

    @Override
    public void tick() {
        if (processing && System.currentTimeMillis() - sentAt >= 5000) {
            processing = false;
            message = "サーバーから応答がありません。もう一度確定してください。";
            detailScroll = 0;
            updateButton();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !processing && !hasOptions();
    }

    @Override
    public void close() {
        if (shouldCloseOnEsc()) super.close();
    }

    private int themeColor() {
        return switch (kind) {
            case ABILITY -> KuTheme.GOLD;
            case RELIC -> KuTheme.CYAN;
            case CURSE -> KuTheme.PURPLE;
        };
    }

    private static int faded(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, KuTheme.BACKDROP);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, KuTheme.PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(hasOptions()
                        ? "カードを1枚選んで確定してください" : "選べるカードはありません。閉じてください。"),
                width / 2, 26, KuTheme.MUTED);
        Layout layout = layout();
        int hovered = hoveredCard(layout, mouseX, mouseY);
        for (int i = 0; i < optionIds.size(); i++) {
            drawCard(context, layout, i, hovered == i);
        }
        drawDetails(context, layout.detailTop(), hovered >= 0 ? hovered : selectedIndex);
        if (selectedIndex >= 0) {
            drawFooterLine(context, "選択中: " + UniqueLoreRegistry.getOrFallback(optionIds.get(selectedIndex)).name()
                    + " ・確定後は変更できません", height - 49, KuTheme.PRIMARY);
        }
        drawFooterLine(context, "←→/数字: 選択  ↑↓: 詳細  Enter: 確定", height - 38, KuTheme.MUTED);
    }

    private void drawFooterLine(DrawContext context, String text, int y, int color) {
        float scale = Math.min(1, (width - 24.0F) / Math.max(1, textRenderer.getWidth(text)));
        context.getMatrices().push();
        context.getMatrices().translate(width / 2.0F, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(text), 0, 0, color);
        context.getMatrices().pop();
    }

    private void drawCard(DrawContext context, Layout layout, int index, boolean hovered) {
        float opacity = progress(index);
        if (opacity <= 0) return;
        int x = cardX(layout, index);
        int y = cardY(layout, index) - (hovered ? 4 : 0);
        int w = layout.cardWidth();
        int h = layout.cardHeight();
        boolean selected = index == selectedIndex;
        int border = faded(hovered ? KuTheme.HOVER : themeColor(), opacity);
        context.fill(x + 3, y + 4, x + w + 3, y + h + 4, faded(KuTheme.SHADOW, opacity));
        context.fill(x, y, x + w, y + h, faded(KuTheme.CARD, opacity));
        KuTheme.drawBorder(context, x, y, w, h, border);
        if (selected || hovered) KuTheme.drawBorder(context, x + 1, y + 1, w - 2, h - 2, border);
        if (selected) {
            KuTheme.drawBorder(context, x - 2, y - 2, w + 4, h + 4, faded(themeColor(), opacity * 0.5F));
        }
        UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(optionIds.get(index));
        context.enableScissor(x + 3, y + 3, x + w - 3, y + h - 3);
        context.fill(x + 4, y + 4, x + 17, y + 16, faded(themeColor(), opacity));
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(index + 1)),
                x + 10, y + 6, faded(KuTheme.INSET, opacity));
        boolean compact = h < 130 || w < 96;
        int iconScale = compact ? 1 : 2;
        context.getMatrices().push();
        context.getMatrices().translate(compact ? x + w - 23 : x + (w - 32) / 2.0F,
                y + (compact ? 5 : 18), 0);
        context.getMatrices().scale(iconScale, iconScale, 1);
        context.drawItem(info.icon(), 0, 0);
        context.getMatrices().pop();
        List<CardLine> lines = new ArrayList<>();
        for (OrderedText line : textRenderer.wrapLines(Text.literal(info.name()), Math.max(1, w - 12))) {
            lines.add(new CardLine(line, KuTheme.PRIMARY));
        }
        lines.add(new CardLine(Text.literal("[" + info.category() + "]").asOrderedText(), themeColor()));
        String[] effects = SelectionIcons.effectsFor(info.id());
        for (int i = 0; i < effects.length; i++) {
            int color = kind == ContentRegistry.Kind.CURSE
                    ? (i < SelectionIcons.meritLineCount(info.id()) ? KuTheme.MERIT : KuTheme.DRAWBACK) : KuTheme.SECONDARY;
            String effect = effects[i].replaceFirst("^_+", "");
            for (OrderedText line : textRenderer.wrapLines(Text.literal(effect), Math.max(1, w - 12))) {
                lines.add(new CardLine(line, color));
            }
        }
        int textTop = compact ? 25 : 55;
        float textScale = Math.min(1, Math.max(1, h - textTop - 5) / (lines.size() * 10.0F));
        context.getMatrices().push();
        context.getMatrices().translate(x + w / 2.0F, y + textTop, 0);
        context.getMatrices().scale(textScale, textScale, 1);
        int lineY = 0;
        for (CardLine line : lines) {
            context.drawTextWithShadow(textRenderer, line.text(), -textRenderer.getWidth(line.text()) / 2,
                    lineY, faded(line.color(), opacity));
            lineY += 10;
        }
        context.getMatrices().pop();
        context.disableScissor();
        if (selected) {
            context.fill(x + w / 2 - 7, y - 6, x + w / 2 + 7, y + 6, faded(themeColor(), opacity));
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("✔"),
                    x + w / 2, y - 4, faded(KuTheme.INSET, opacity));
        }
    }

    private record CardLine(OrderedText text, int color) {}

    private void drawDetails(DrawContext context, int top, int index) {
        int bottom = height - 55;
        if (top >= bottom) return;
        if (detailIndex != index) {
            detailIndex = index;
            detailScroll = 0;
        }
        context.fill(12, top, width - 12, bottom, KuTheme.PANEL);
        KuTheme.drawBorder(context, 12, top, width - 24, bottom - top, KuTheme.BORDER);
        int contentBottom = bottom - 15;
        context.enableScissor(18, top + 4, width - 18, contentBottom);
        int y = top + 6 - detailScroll;
        if (!message.isEmpty()) {
            y = drawWrapped(context, message, y, KuTheme.DRAWBACK) + 4;
        }
        if (index < 0) {
            y = drawWrapped(context, hasOptions() ? "カードをクリック・数字キー・←→で選ぶと詳細が出ます"
                    : "残りのカードはありません。", y, KuTheme.MUTED);
        } else {
            UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(optionIds.get(index));
            y = drawWrapped(context, info.name() + "  [" + info.category() + "]", y, themeColor());
            y = drawWrapped(context, "条件: " + info.triggerInfo(), y + 2, KuTheme.SECONDARY);
            for (String line : info.descriptionLines()) {
                y = drawWrapped(context, line, y + 2, line.contains("【代償】") ? KuTheme.DRAWBACK : KuTheme.BODY);
            }
        }
        detailMaxScroll = Math.max(0, y + detailScroll - contentBottom);
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        context.disableScissor();
        if (detailMaxScroll > 0) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("詳細: ホイール / ↑↓ でスクロール"),
                    width / 2, bottom - 12, KuTheme.MUTED);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (processing) return true;
        if (mouseX >= 12 && mouseX < width - 12
                && mouseY >= layout().detailTop() && mouseY < height - 55 && detailMaxScroll > 0) {
            detailScroll = Math.clamp(detailScroll - (int) Math.round(verticalAmount * 20), 0, detailMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int drawWrapped(DrawContext context, String text, int y, int color) {
        for (OrderedText line : textRenderer.wrapLines(Text.literal(text), Math.max(1, width - 40))) {
            context.drawTextWithShadow(textRenderer, line, 20, y, color);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (processing) return true;
        if (button == 0) {
            int index = hoveredCard(layout(), mouseX, mouseY);
            if (index >= 0) {
                select(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean shouldPause() {
        return false;
    }
}
