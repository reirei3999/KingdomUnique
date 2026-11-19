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
    private static final int CARD_BG = 0xCC1E1E1E;
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xAA000000);

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

        // 背景・枠線（等倍・整数座標）。
        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, CARD_BG);
        context.fill(x, y, x + CARD_WIDTH, y + 2, frame);
        context.fill(x, y + CARD_HEIGHT - 2, x + CARD_WIDTH, y + CARD_HEIGHT, frame);
        context.fill(x, y, x + 2, y + CARD_HEIGHT, frame);
        context.fill(x + CARD_WIDTH - 2, y, x + CARD_WIDTH, y + CARD_HEIGHT, frame);

        // アイコンは等倍でくっきり描画（拡大するとテクスチャがぼやける）。
        ItemStack icon = SelectionIcons.iconFor(id);
        if (!icon.isEmpty()) {
            context.drawItem(icon, (int) (x + 12), (int) (y + 12), 150);
        }

        // テキストは等倍・整数座標。長文は wrapLines で等倍のまま改行する。
        context.getMatrices().push();
        context.getMatrices().scale(1.0F, 1.0F, 1.0F);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(ContentRegistry.nameOf(id)).formatted(Formatting.BOLD, Formatting.WHITE),
                (int) (x + 44), (int) (y + 12), TEXT_WHITE);

        String[] lines = descriptionLines(id);
        int textY = y + 30;
        int limit = y + CARD_HEIGHT - 26;
        for (String line : lines) {
            for (OrderedText wrapped : this.textRenderer.wrapLines(Text.literal(line), CARD_WIDTH - 12)) {
                if (textY > limit) {
                    break;
                }
                context.drawTextWithShadow(this.textRenderer, wrapped, (int) (x + 6), (int) textY,
                        0xFFDDDDDD);
                textY += 10;
            }
        }
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("操作: " + AbilityDescriptions.controlHint(id)),
                (int) (x + 6), (int) (y + CARD_HEIGHT - 16), 0xFFFFDD55);
        context.getMatrices().pop();
    }

    private String[] descriptionLines(String id) {
        if (ContentRegistry.kindOf(id) == ContentRegistry.Kind.ABILITY) {
            return AbilityDescriptions.details(id);
        }
        return SelectionIcons.effectsFor(id);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
