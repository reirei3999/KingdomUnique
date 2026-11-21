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

/**
 * ユニーク選択画面（Gemini 提示のホバー詳細プレビュー付き2ペインUI）。
 * 世界排他でやり直しが効かないため、確定前に必ず効果と代償を確認できるようにする。
 */
@Environment(EnvType.CLIENT)
public final class SelectionScreen extends Screen {

    private final ContentRegistry.Kind kind;
    private final List<String> optionIds;
    private int selectedIndex = -1;
    private ButtonWidget decideButton;

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
        int[] p = panel();
        int left = (this.width - p[0]) / 2;
        int top = (this.height - p[1]) / 2;

        decideButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§a§lこの内容で確定する"), btn -> decide())
                .dimensions(left + p[0] - 140, top + p[1] - 28, 130, 20).build());
        decideButton.active = false;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§7保留して閉じる"), btn -> this.close())
                .dimensions(left + 10, top + p[1] - 28, 100, 20).build());
    }

    /** {幅, 高さ} */
    private int[] panel() {
        return new int[]{Math.min(430, this.width - 20), Math.min(260, this.height - 20)};
    }

    private int buttonY(int i) {
        int[] p = panel();
        int spacing = optionIds.size() >= 5 ? 28 : 34;
        return (this.height - p[1]) / 2 + 34 + i * spacing;
    }

    private int buttonHeight() {
        return optionIds.size() >= 5 ? 24 : 28;
    }

    /** カード（ボタン）をクリックしたら選択状態にする（まだ送信しない）。 */
    private void select(int index) {
        if (index < 0 || index >= optionIds.size()) {
            return;
        }
        selectedIndex = index;
        if (decideButton != null) {
            decideButton.active = true;
        }
    }

    /** 数字キー 1〜9 でも選択できる。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < optionIds.size()) {
                select(index);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void decide() {
        if (selectedIndex < 0 || selectedIndex >= optionIds.size()) {
            return;
        }
        String chosen = optionIds.get(selectedIndex);
        if ("none".equals(chosen)) {
            this.close();
            return;
        }
        ClientPlayNetworking.send(new SelectionChoicePayload(kind, chosen));
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.playSound(
                    kind == ContentRegistry.Kind.CURSE
                            ? SoundEvents.ENTITY_WITHER_SPAWN
                            : SoundEvents.ENTITY_PLAYER_LEVELUP,
                    0.8F, 1.0F);
        }
        this.close();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF0B0B10);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int[] p = panel();
        int left = (this.width - p[0]) / 2;
        int top = (this.height - p[1]) / 2;

        // ホバー中候補をプレビュー対象に切り替える（未確定でも詳細が見える）。
        int leftX = left + 10;
        int btnW = 148;
        for (int i = 0; i < optionIds.size(); i++) {
            int y = buttonY(i);
            if (mouseX >= leftX && mouseX <= leftX + btnW
                    && mouseY >= y && mouseY <= y + buttonHeight()) {
                selectedIndex = i;
                if (decideButton != null) {
                    decideButton.active = true;
                }
            }
        }

        int previewIndex = selectedIndex < 0 ? 0 : Math.min(selectedIndex, optionIds.size() - 1);
        UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(optionIds.get(previewIndex));
        int theme = themeColor(info.category());

        context.fill(left, top, left + p[0], top + p[1], 0xEC0B1120);
        drawBorder(context, left, top, p[0], p[1], theme);
        context.fill(left + 1, top + 1, left + p[0] - 1, top + 25, 0xFF1E293B);
        context.drawTextWithShadow(this.textRenderer, Text.literal(titleOf(kind)),
                left + 10, top + 8, 0xFFFFFF);

        // 左：候補ボタン（自前描画、ボタンWidgetの背景に覆われないようにする）。
        for (int i = 0; i < optionIds.size(); i++) {
            String id = optionIds.get(i);
            UniqueLoreRegistry.UniqueInfo e = UniqueLoreRegistry.getOrFallback(id);
            int y = buttonY(i);
            context.fill(leftX, y, leftX + btnW, y + buttonHeight(), 0xFF161B26);
            drawBorder(context, leftX, y, btnW, buttonHeight(),
                    i == previewIndex ? theme : 0xFF475569);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("§e[" + (i + 1) + "] §f" + e.name()), leftX + 6, y + 6, 0xFFFFFF);
        }

        int dividerX = left + 166;
        context.fill(dividerX, top + 30, dividerX + 1, top + p[1] - 34, 0x5094A3B8);

        // 右：詳細プレビュー。
        int detailX = dividerX + 10;
        int detailY = top + 34;
        int detailW = (left + p[0]) - detailX - 10;

        context.drawItem(info.icon(), detailX, detailY);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§f§l" + info.name()),
                detailX + 22, detailY + 1, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§e[" + info.category() + "]"),
                detailX + 22, detailY + 11, 0xFFFFFF);
        detailY += 24;
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§7条件: §f" + info.triggerInfo()), detailX, detailY, 0xFFFFFF);
        detailY += 13;
        context.fill(detailX, detailY, detailX + detailW, detailY + 1, 0x40FFFFFF);
        detailY += 6;

        for (String line : info.descriptionLines()) {
            String formatted = line.replace("【代償】", "§c【代償】§f");
            for (OrderedText wrapped : this.textRenderer.wrapLines(Text.literal(formatted), detailW)) {
                if (detailY > top + p[1] - 34) {
                    break;
                }
                context.drawTextWithShadow(this.textRenderer, wrapped, detailX, detailY, 0xE2E8F0);
                detailY += 11;
            }
            detailY += 2;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /** 選択中カードをクリックで確定する（自前描画ボタンなので mouseClicked で拾う）。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int[] p = panel();
        int left = (this.width - p[0]) / 2;
        int top = (this.height - p[1]) / 2;
        if (button == 0 && mouseX >= leftX(left) && mouseX <= leftX(left) + 148) {
            for (int i = 0; i < optionIds.size(); i++) {
                int y = buttonY(i);
                if (mouseY >= y && mouseY <= y + buttonHeight()) {
                    select(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int leftX(int left) {
        return left + 10;
    }

    private static int themeColor(String category) {
        if (category.contains("呪物")) {
            return 0xFFA855F7;
        }
        if (category.contains("遺物")) {
            return 0xFF38BDF8;
        }
        return 0xFFE5B84B;
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
