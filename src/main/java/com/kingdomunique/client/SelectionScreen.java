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
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** バニラ風の半透明オーバーレイ＋カード型選択画面。 */
@Environment(EnvType.CLIENT)
public final class SelectionScreen extends Screen {

    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 150;
    private static final int CARD_GAP = 12;
    private static final int FRAME_NORMAL = 0xFF8B0000;
    private static final int FRAME_HIGHLIGHT = 0xFFFFF0A0;
    // 半透明だと背面のぼかしが透けて文字が滲むため、カード背景は完全不透明にする。
    private static final int CARD_BG = 0xFF1E1E1E;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_SUBTLE = 0xFFFFFFFF;
    private static final int TEXT_EFFECT = 0xFFFFFFFF;
    private static final int TEXT_MERIT = 0xFF55FFFF;
    private static final int TEXT_DEMERIT = 0xFFFF5555;

    private final ContentRegistry.Kind kind;
    private final List<String> optionIds;
    private int selectedIndex = -1;
    private ButtonWidget decideButton;

    public SelectionScreen(ContentRegistry.Kind kind, List<String> optionIds) {
        super(Text.literal(titleOf(kind)));
        this.kind = kind;
        this.optionIds = List.copyOf(optionIds);
    }

    private static String titleOf(ContentRegistry.Kind kind) {
        return switch (kind) {
            case ABILITY -> "能力の選択";
            case RELIC -> "遺物の選択";
            case CURSE -> "呪物の選択";
        };
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonY = cardTop() + CARD_HEIGHT + 16;
        decideButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("決定"), button -> decide())
                .dimensions(centerX - 110, buttonY, 105, 20).build());
        decideButton.active = false;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("閉じる"), button -> this.close())
                .dimensions(centerX + 5, buttonY, 105, 20).build());
    }

    private int cardTop() {
        return Math.max(58, this.height / 2 - CARD_HEIGHT / 2 - 20);
    }

    /** カード自体はボタンWidgetにしない（ボタン背景が内容を覆い隠すため）。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && optionIds.size() > 0) {
            int totalWidth = optionIds.size() * CARD_WIDTH + (optionIds.size() - 1) * CARD_GAP;
            int startX = this.width / 2 - totalWidth / 2;
            int top = cardTop();
            for (int i = 0; i < optionIds.size(); i++) {
                int x = startX + i * (CARD_WIDTH + CARD_GAP);
                if (mouseX >= x && mouseX <= x + CARD_WIDTH
                        && mouseY >= top && mouseY <= top + CARD_HEIGHT) {
                    select(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private void select(int index) {
        selectedIndex = index;
        if (decideButton != null) {
            decideButton.active = true;
        }
    }

    private void decide() {
        if (selectedIndex < 0 || selectedIndex >= optionIds.size()) {
            return;
        }
        String chosen = optionIds.get(selectedIndex);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        context.getMatrices().push();
        context.getMatrices().scale(1.0F, 1.0F, 1.0F);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                (int) (this.width / 2), (int) 18, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("カードをクリック（または数字キー）で選択 → 「決定」を押してください"),
                (int) (this.width / 2), (int) 36, TEXT_SUBTLE);

        if (optionIds.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("このカテゴリには選択できるアイテムが残っていません。"),
                    (int) (this.width / 2), (int) (this.height / 2), 0xFFFF6666);
            context.getMatrices().pop();
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        context.getMatrices().pop();

        int totalWidth = optionIds.size() * CARD_WIDTH + (optionIds.size() - 1) * CARD_GAP;
        int startX = this.width / 2 - totalWidth / 2;
        int top = cardTop();

        for (int i = 0; i < optionIds.size(); i++) {
            String id = optionIds.get(i);
            int x = startX + i * (CARD_WIDTH + CARD_GAP);
            boolean hovered = mouseX >= x && mouseX <= x + CARD_WIDTH
                    && mouseY >= top && mouseY <= top + CARD_HEIGHT;
            drawCard(context, id, x, top, hovered || i == selectedIndex, i);
        }

        context.getMatrices().push();
        context.getMatrices().scale(1.0F, 1.0F, 1.0F);
        String status = selectedIndex < 0
                ? "未選択 — カードをクリックしてください"
                : "選択中: " + ContentRegistry.nameOf(optionIds.get(selectedIndex)) + " — 「決定」で確定";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
                (int) (this.width / 2), (int) (cardTop() + CARD_HEIGHT + 4),
                selectedIndex < 0 ? 0xFFFF6666 : 0xFF55FF55);
        context.getMatrices().pop();

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext context, String id, int x, int y, boolean highlight, int index) {
        int frame = highlight ? FRAME_HIGHLIGHT : frameColor();

        // 一切の拡大変換を行わない（小数スケールはテクスチャも文字もぼかす）。
        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, CARD_BG);
        context.fill(x, y, x + CARD_WIDTH, y + 2, frame);
        context.fill(x, y + CARD_HEIGHT - 2, x + CARD_WIDTH, y + CARD_HEIGHT, frame);
        context.fill(x, y, x + 2, y + CARD_HEIGHT, frame);
        context.fill(x + CARD_WIDTH - 2, y, x + CARD_WIDTH, y + CARD_HEIGHT, frame);
        if (highlight) {
            context.fill(x + 3, y + 3, x + CARD_WIDTH - 3, y + 5, FRAME_HIGHLIGHT);
            context.fill(x + 3, y + CARD_HEIGHT - 5, x + CARD_WIDTH - 3, y + CARD_HEIGHT - 3, FRAME_HIGHLIGHT);
        }

        // アイコンは等倍（16x16）でくっきり描画する。
        ItemStack icon = SelectionIcons.iconFor(id);
        if (!icon.isEmpty()) {
            int ix = (int) (x + CARD_WIDTH / 2.0F) - 8;
            int iy = y + 30;
            context.fill(ix - 3, iy - 3, ix + 19, iy + 19, 0xFF000000);
            context.drawItem(icon, ix, iy);
        }

        // テキストは Z オフセットを付けず、描画順で手前に重ねる（等倍・整数座標）。
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("[" + (index + 1) + "]").formatted(Formatting.YELLOW),
                (int) (x + 6), (int) (y + 6), 0xFFFFFF55);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(ContentRegistry.nameOf(id)).formatted(Formatting.BOLD, Formatting.WHITE),
                (int) (x + CARD_WIDTH / 2), (int) (y + 78), TEXT_WHITE);

        String[] effects = SelectionIcons.effectsFor(id);
        int meritCount = kind == ContentRegistry.Kind.CURSE ? SelectionIcons.meritLineCount(id) : effects.length;
        int textY = y + 96;
        int limitY = y + CARD_HEIGHT - 8;
        for (int i = 0; i < effects.length; i++) {
            int color = kind == ContentRegistry.Kind.CURSE
                    ? (i < meritCount ? TEXT_MERIT : TEXT_DEMERIT)
                    : TEXT_EFFECT;
            List<OrderedText> lines = this.textRenderer.wrapLines(
                    Text.literal("・" + effects[i].replace("_", "")), CARD_WIDTH - 12);
            for (OrderedText line : lines) {
                if (textY > limitY) {
                    break;
                }
                context.drawTextWithShadow(this.textRenderer, line, (int) (x + 6), (int) textY, color);
                textY += 10;
            }
        }
    }

    private int frameColor() {
        return switch (kind) {
            case ABILITY -> 0xFFAAAA55;
            case RELIC -> 0xFF55CCFF;
            case CURSE -> FRAME_NORMAL;
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
