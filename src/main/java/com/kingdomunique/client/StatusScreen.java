package com.kingdomunique.client;

import com.kingdomunique.network.SyncPlayerStatusPayload;
import com.kingdomunique.registry.AbilityDescriptions;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** 所持している能力・遺物・呪物の効果と操作方法を見返す確認画面。 */
@Environment(EnvType.CLIENT)
public final class StatusScreen extends Screen {

    private static final int CARD_WIDTH = 210;
    private static final int CARD_HEIGHT = 116;
    private static final int CARD_GAP = 10;
    private static final int COLUMNS = 3;
    private static final int ROWS = 2;
    private static final int PER_PAGE = COLUMNS * ROWS;

    private static final int FRAME = 0xFF8B0000;
    private static final int CARD_BG = 0xFF1E1E1E;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_SUBTLE = 0xFFAAAAAA;

    private final List<String> ids;
    private final String abilityId;
    private final int cooldownTicks;
    private final int miningRange;
    private int page = 0;

    public StatusScreen(SyncPlayerStatusPayload payload) {
        super(Text.literal("所持能力・遺物・呪物 一覧"));
        this.ids = List.copyOf(payload.ownedIds());
        this.abilityId = payload.abilityId();
        this.cooldownTicks = payload.cooldownTicks();
        this.miningRange = payload.miningRange();
    }

    @Override
    protected void init() {
        int maxPage = Math.max(0, (ids.size() - 1) / PER_PAGE);
        int y = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("前へ"), button -> {
            page = Math.max(0, page - 1);
        }).dimensions(this.width / 2 - 110, y, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("閉じる"), button -> this.close())
                .dimensions(this.width / 2 - 5, y, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("次へ"), button -> {
            page = Math.min(maxPage, page + 1);
        }).dimensions(this.width / 2 + 100, y, 100, 20).build());
    }

    private int gridTop() {
        return 52;
    }

    /** 世界のぼかしをやめて単色背景にする（文字のフリンジ防止）。 */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF0E0E12);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // テキストは必ず等倍・整数座標で描画する（拡大縮小でフォントがにじまないように）。
        context.getMatrices().push();
        context.getMatrices().scale(1.0F, 1.0F, 1.0F);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                (int) (this.width / 2), (int) 16, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Kキー / /ku info でいつでも確認できます（ページ " + (page + 1) + "）"),
                (int) (this.width / 2), (int) 34, TEXT_SUBTLE);

        if (ids.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("まだ能力・遺物・呪物を所持していません。"),
                    (int) (this.width / 2), (int) (this.height / 2), 0xFFFF6666);
            context.getMatrices().pop();
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        context.getMatrices().pop();

        int totalWidth = COLUMNS * CARD_WIDTH + (COLUMNS - 1) * CARD_GAP;
        int startX = this.width / 2 - totalWidth / 2;
        int startIndex = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && startIndex + i < ids.size(); i++) {
            String id = ids.get(startIndex + i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            drawCard(context, id, startX + col * (CARD_WIDTH + CARD_GAP),
                    gridTop() + row * (CARD_HEIGHT + CARD_GAP));
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext context, String id, int x, int y) {
        ContentRegistry.Kind kind = ContentRegistry.kindOf(id);
        int frame = kind == ContentRegistry.Kind.RELIC ? 0xFF55CCFF
                : kind == ContentRegistry.Kind.CURSE ? 0xFFFF5555 : 0xFFAAAA55;
        String category = kind == ContentRegistry.Kind.RELIC ? "遺物"
                : kind == ContentRegistry.Kind.CURSE ? "呪物" : "能力";

        // 影→本体→枠→ヘッダーの順に重ねて立体感を出す（等倍・整数座標）。
        context.fill(x + 3, y + 3, x + CARD_WIDTH + 3, y + CARD_HEIGHT + 3, 0x88000000);
        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, CARD_BG);
        context.fill(x, y, x + CARD_WIDTH, y + 2, frame);
        context.fill(x, y + CARD_HEIGHT - 2, x + CARD_WIDTH, y + CARD_HEIGHT, frame);
        context.fill(x, y, x + 2, y + CARD_HEIGHT, frame);
        context.fill(x + CARD_WIDTH - 2, y, x + CARD_WIDTH, y + CARD_HEIGHT, frame);
        context.fill(x + 2, y + 2, x + CARD_WIDTH - 2, y + 18, 0xFF2A2A30);
        context.fill(x + 2, y + 18, x + CARD_WIDTH - 2, y + 19, frame);

        context.drawText(this.textRenderer,
                Text.literal("[" + category + "]").formatted(Formatting.YELLOW),
                x + 6, y + 5, 0xFFFFFF55, false);
        context.drawText(this.textRenderer,
                Text.literal(ContentRegistry.nameOf(id)).formatted(Formatting.BOLD),
                x + 52, y + 5, TEXT_WHITE, false);

        ItemStack icon = SelectionIcons.iconFor(id);
        if (!icon.isEmpty()) {
            context.fill(x + 6, y + 26, x + 26, y + 46, 0xFF101014);
            context.fill(x + 6, y + 26, x + 26, y + 27, frame);
            context.fill(x + 6, y + 45, x + 26, y + 46, frame);
            context.drawItem(icon, x + 8, y + 28);
        }

        int textY = y + 28;
        int limit = y + CARD_HEIGHT - 24;
        for (String line : AbilityDescriptions.details(id)) {
            for (OrderedText wrapped : this.textRenderer.wrapLines(
                    Text.literal(line), CARD_WIDTH - 36)) {
                if (textY > limit) {
                    break;
                }
                context.drawText(this.textRenderer, wrapped, x + 32, textY, 0xFFFFFFFF, false);
                textY += 11;
            }
        }

        context.fill(x + 2, y + CARD_HEIGHT - 21, x + CARD_WIDTH - 2,
                y + CARD_HEIGHT - 2, 0xFF1A1A20);
        context.drawText(this.textRenderer,
                Text.literal("操作: " + AbilityDescriptions.controlHint(id)),
                x + 6, y + CARD_HEIGHT - 17, 0xFFFFDD55, false);
    }
    @Override
    public boolean shouldPause() {
        return false;
    }
}
