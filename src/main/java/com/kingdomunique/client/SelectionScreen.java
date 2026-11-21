package com.kingdomunique.client;

import com.kingdomunique.network.SelectionChoicePayload;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class SelectionScreen extends Screen {
    private final ContentRegistry.Kind kind;
    private final List<String> optionIds;
    private int selectedIndex = -1;
    private ButtonWidget decideButton;
    private long openedAt;
    private long lastClickAt;
    private int lastClickIndex = -1;
    private int detailIndex = -1;
    private int detailScroll;
    private int detailMaxScroll;

    public SelectionScreen(ContentRegistry.Kind kind, List<String> optionIds) {
        super(Text.literal(titleOf(kind)));
        this.kind = kind;
        this.optionIds = optionIds == null || optionIds.isEmpty()
                ? List.of("none") : List.copyOf(optionIds);
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
        lastClickIndex = -1;
        detailIndex = -1;
        detailScroll = 0;
        detailMaxScroll = 0;
        int buttonWidth = Math.min(150, (width - 30) / 2);
        decideButton = addDrawableChild(ButtonWidget.builder(Text.literal("§a§lこのカードで確定"),
                button -> decide()).dimensions(width / 2 + 5, height - 26, buttonWidth, 20).build());
        decideButton.active = selectedIndex >= 0;
        addDrawableChild(ButtonWidget.builder(Text.literal("§7保留して閉じる"), button -> close())
                .dimensions(width / 2 - buttonWidth - 5, height - 26, buttonWidth, 20).build());
    }

    private record Layout(int columns, int cardWidth, int cardHeight, int detailTop) {}

    private Layout layout() {
        int available = Math.max(1, width - 24);
        int columns = Math.max(1, available / 100);
        int rows = optionIds.size() > columns ? 2 : 1;
        columns = Math.min(optionIds.size(), Math.max(columns, (optionIds.size() + rows - 1) / rows));
        int cardWidth = Math.max(1, Math.min(120, (available - (columns - 1) * 8) / columns));
        int cardHeight = Math.max(32, Math.min(145, (height - 144) / rows - 8));
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
        if (index >= 0 && index < optionIds.size()) {
            selectedIndex = index;
            decideButton.active = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9
                && keyCode - GLFW.GLFW_KEY_1 < optionIds.size()) {
            select(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && selectedIndex >= 0) {
            decide();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void decide() {
        if (selectedIndex < 0 || selectedIndex >= optionIds.size()) {
            return;
        }
        String chosen = optionIds.get(selectedIndex);
        if ("none".equals(chosen)) {
            close();
            return;
        }
        ClientPlayNetworking.send(new SelectionChoicePayload(kind, chosen));
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.playSound(
                    kind == ContentRegistry.Kind.CURSE
                            ? SoundEvents.ENTITY_WITHER_SPAWN : SoundEvents.ENTITY_PLAYER_LEVELUP,
                    0.8F, 1.0F);
        }
        close();
    }

    private int themeColor() {
        return switch (kind) {
            case ABILITY -> 0xFFE5B84B;
            case RELIC -> 0xFF38BDF8;
            case CURSE -> 0xFFA855F7;
        };
    }

    private static int faded(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & 0xFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF0B0B10);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("1枚だけ選べます・世界で唯一・やり直し不可"),
                width / 2, 26, 0xFF94A3B8);
        Layout layout = layout();
        int hovered = hoveredCard(layout, mouseX, mouseY);
        for (int i = 0; i < optionIds.size(); i++) {
            drawCard(context, layout, i, hovered == i);
        }
        drawDetails(context, layout.detailTop(), hovered >= 0 ? hovered : selectedIndex);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext context, Layout layout, int index, boolean hovered) {
        float opacity = progress(index);
        if (opacity <= 0) return;
        int x = cardX(layout, index);
        int y = cardY(layout, index) - (hovered ? 4 : 0);
        int w = layout.cardWidth();
        int h = layout.cardHeight();
        boolean selected = index == selectedIndex;
        int border = faded(hovered ? 0xFFFFF2CE : themeColor(), opacity);
        context.fill(x + 3, y + 4, x + w + 3, y + h + 4, faded(0x90000000, opacity));
        context.fill(x, y, x + w, y + h, faded(0xFF161B2A, opacity));
        drawBorder(context, x, y, w, h, border);
        if (selected || hovered) drawBorder(context, x + 1, y + 1, w - 2, h - 2, border);
        if (selected) {
            drawBorder(context, x - 2, y - 2, w + 4, h + 4, faded(themeColor(), opacity * 0.5F));
        }
        UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(optionIds.get(index));
        context.enableScissor(x + 3, y + 3, x + w - 3, y + h - 3);
        context.fill(x + 4, y + 4, x + 17, y + 16, faded(themeColor(), opacity));
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(index + 1)),
                x + 10, y + 6, faded(0xFF0B1120, opacity));
        if (h >= 64) {
            context.getMatrices().push();
            context.getMatrices().translate(x + (w - 32) / 2.0F, y + (h >= 100 ? 18 : 6), 0);
            context.getMatrices().scale(2, 2, 1);
            context.drawItem(info.icon(), 0, 0);
            context.getMatrices().pop();
        }
        int lineY = y + (h >= 100 ? 55 : h >= 64 ? 41 : 19);
        for (OrderedText line : textRenderer.wrapLines(Text.literal(info.name()), Math.max(1, w - 12))) {
            if (lineY > y + h - 13) break;
            context.drawTextWithShadow(textRenderer, line, x + (w - textRenderer.getWidth(line)) / 2,
                    lineY, faded(0xFFFFFFFF, opacity));
            lineY += 10;
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("[" + info.category() + "]"),
                x + w / 2, lineY + 2, faded(themeColor(), opacity));
        lineY += 17;
        String[] effects = SelectionIcons.effectsFor(info.id());
        for (int i = 0; i < effects.length; i++) {
            int color = kind == ContentRegistry.Kind.CURSE
                    ? (i < SelectionIcons.meritLineCount(info.id()) ? 0xFF86EFAC : 0xFFF87171) : 0xFFCBD5E1;
            String effect = effects[i].replaceFirst("^_+", "");
            for (OrderedText line : textRenderer.wrapLines(Text.literal(effect), Math.max(1, w - 12))) {
                if (lineY > y + h - (selected ? 23 : 13)) break;
                context.drawTextWithShadow(textRenderer, line, x + (w - textRenderer.getWidth(line)) / 2,
                        lineY, faded(color, opacity));
                lineY += 10;
            }
        }
        if (selected) {
            context.fill(x + 3, y + h - 16, x + w - 3, y + h - 3, faded(themeColor(), opacity));
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("選択中"),
                    x + w / 2, y + h - 13, faded(0xFF0B1120, opacity));
        }
        context.disableScissor();
    }

    private void drawDetails(DrawContext context, int top, int index) {
        int bottom = height - 34;
        if (top >= bottom) return;
        if (detailIndex != index) {
            detailIndex = index;
            detailScroll = 0;
        }
        context.fill(12, top, width - 12, bottom, 0xFF111827);
        drawBorder(context, 12, top, width - 24, bottom - top, 0xFF334155);
        int contentBottom = bottom - 15;
        context.enableScissor(18, top + 4, width - 18, contentBottom);
        int y = top + 6 - detailScroll;
        if (index < 0) {
            y = drawWrapped(context, "カードにカーソルを合わせると詳細が見えます", y, 0xFF94A3B8);
        } else {
            UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(optionIds.get(index));
            y = drawWrapped(context, info.name() + "  [" + info.category() + "]", y, themeColor());
            y = drawWrapped(context, "条件: " + info.triggerInfo(), y + 2, 0xFFCBD5E1);
            for (String line : info.descriptionLines()) {
                y = drawWrapped(context, line, y + 2, line.contains("【代償】") ? 0xFFF87171 : 0xFFE2E8F0);
            }
        }
        detailMaxScroll = Math.max(0, y + detailScroll - contentBottom);
        detailScroll = Math.min(detailScroll, detailMaxScroll);
        context.disableScissor();
        if (detailMaxScroll > 0) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("詳細はホイールでスクロール"),
                    width / 2, bottom - 12, 0xFF94A3B8);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= 12 && mouseX < width - 12
                && mouseY >= layout().detailTop() && mouseY < height - 34 && detailMaxScroll > 0) {
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
        if (button == 0) {
            int index = hoveredCard(layout(), mouseX, mouseY);
            if (index >= 0) {
                long now = System.currentTimeMillis();
                boolean confirm = selectedIndex == index && lastClickIndex == index && now - lastClickAt < 250;
                select(index);
                lastClickIndex = index;
                lastClickAt = now;
                if (confirm) decide();
                return true;
            }
        }
        lastClickIndex = -1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
