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

import java.util.List;

/** バニラ風の半透明オーバーレイ＋カード型選択画面。 */
@Environment(EnvType.CLIENT)
public final class SelectionScreen extends Screen {

    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 150;
    private static final int CARD_GAP = 12;
    private static final int FRAME_NORMAL = 0xFF8B0000;
    private static final int FRAME_HIGHLIGHT = 0xFFFFF0A0;
    private static final int CARD_BG = 0xCC1E1E1E;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_SUBTLE = 0xFFAAAAAA;
    private static final int TEXT_EFFECT = 0xFFDDDDDD;
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
        int totalWidth = optionIds.size() * CARD_WIDTH + (optionIds.size() - 1) * CARD_GAP;
        int startX = centerX - totalWidth / 2;
        for (int i = 0; i < optionIds.size(); i++) {
            int x = startX + i * (CARD_WIDTH + CARD_GAP);
            final int index = i;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(""), button -> {
                selectedIndex = index;
                if (decideButton != null) {
                    decideButton.active = true;
                }
            }).dimensions(x, cardTop(), CARD_WIDTH, CARD_HEIGHT).build());
        }

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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("1つだけ選んでください（世界で唯一のアイテムです）"),
                this.width / 2, 36, TEXT_SUBTLE);

        if (optionIds.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("このカテゴリには選択できるアイテムが残っていません。"),
                    this.width / 2, this.height / 2, 0xFFFF6666);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        int totalWidth = optionIds.size() * CARD_WIDTH + (optionIds.size() - 1) * CARD_GAP;
        int startX = this.width / 2 - totalWidth / 2;
        int top = cardTop();

        for (int i = 0; i < optionIds.size(); i++) {
            String id = optionIds.get(i);
            int x = startX + i * (CARD_WIDTH + CARD_GAP);
            boolean hovered = mouseX >= x && mouseX <= x + CARD_WIDTH
                    && mouseY >= top && mouseY <= top + CARD_HEIGHT;
            drawCard(context, id, x, top, hovered || i == selectedIndex);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext context, String id, int x, int y, boolean highlight) {
        int frame = highlight ? FRAME_HIGHLIGHT : frameColor();

        // 1) 背景・枠線・アイコンのみ拡大変換（1.05倍）の中で描画する。
        //    テキストは拡大変換を閉じた後に等倍で描画しないと文字がぼやける。
        context.getMatrices().push();
        if (highlight) {
            float cx = x + CARD_WIDTH / 2.0F;
            float cy = y + CARD_HEIGHT / 2.0F;
            context.getMatrices().translate(cx, cy, 0.0F);
            context.getMatrices().scale(1.05F, 1.05F, 1.0F);
            context.getMatrices().translate(-cx, -cy, 0.0F);
        }

        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, CARD_BG);
        context.fill(x, y, x + CARD_WIDTH, y + 2, frame);
        context.fill(x, y + CARD_HEIGHT - 2, x + CARD_WIDTH, y + CARD_HEIGHT, frame);
        context.fill(x, y, x + 2, y + CARD_HEIGHT, frame);
        context.fill(x + CARD_WIDTH - 2, y, x + CARD_WIDTH, y + CARD_HEIGHT, frame);
        if (highlight) {
            context.fill(x + 3, y + 3, x + CARD_WIDTH - 3, y + 5, FRAME_HIGHLIGHT);
            context.fill(x + 3, y + CARD_HEIGHT - 5, x + CARD_WIDTH - 3, y + CARD_HEIGHT - 3, FRAME_HIGHLIGHT);
        }

        ItemStack icon = SelectionIcons.iconFor(id);
        if (!icon.isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(x + CARD_WIDTH / 2.0F, y + 40, 150.0F);
            context.getMatrices().scale(2.5F, 2.5F, 1.0F);
            context.drawItem(icon, -8, -8, 150);
            context.getMatrices().pop();
        }
        // 拡大変換をここで終了（等倍に戻す）。
        context.getMatrices().pop();

        // 2) テキスト: Z方向のみ整数で平行移動し、すべて整数座標・等倍で描画。
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);

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
        context.getMatrices().pop();
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
