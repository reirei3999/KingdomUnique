package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.item.ItemCatalog;
import com.kingdomunique.network.RequestItemPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 専用アイテムを一覧から取得するメニュー。 */
@Environment(EnvType.CLIENT)
public final class ItemMenuScreen extends Screen {

    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    public ItemMenuScreen() {
        super(Text.literal("KingdomUnique 専用アイテム"));
    }

    @Override
    protected void init() {
        var entries = ItemCatalog.entries();
        // GUIスケール4（幅約480px）でも全項目が見えるように列数とボタン幅を動的に決める。
        int columns = Math.max(3, Math.min(5, this.width / 100));
        int rows = (entries.size() + columns - 1) / columns;
        int buttonWidth = Math.max(56, (this.width - 24 - (columns - 1) * GAP) / columns);
        int totalWidth = columns * buttonWidth + (columns - 1) * GAP;
        int startX = (this.width - totalWidth) / 2;

        int totalHeight = rows * (BUTTON_HEIGHT + GAP);
        int startY = panelTop() + 40;
        int maxStartY = this.height - totalHeight - 28;
        if (maxStartY < startY) {
            startY = Math.max(2, maxStartY);
        }

        for (int i = 0; i < entries.size(); i++) {
            ItemCatalog.Entry entry = entries.get(i);
            int x = startX + (i % columns) * (buttonWidth + GAP);
            int y = startY + (i / columns) * (BUTTON_HEIGHT + GAP);
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(entry.label()).formatted(Formatting.WHITE),
                    button -> ClientPlayNetworking.send(new RequestItemPayload(entry.id())))
                    .dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("閉じる"), button -> this.close())
                .dimensions(this.width / 2 - 45,
                        Math.min(this.height - 22, startY + totalHeight + 4), 90, 18)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // テキストは等倍・整数座標で描画する。
        context.getMatrices().push();
        context.getMatrices().scale(1.0F, 1.0F, 1.0F);
        KuTheme.drawTitle(context, textRenderer, title, width / 2, panelTop() + 8);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("クリックでアイテムを入手（ユニーク武器は世界で一つだけ）"),
                (int) (this.width / 2), panelTop() + 26, KuTheme.MUTED);
        context.getMatrices().pop();
    }

    private int panelTop() {
        int columns = Math.max(3, Math.min(5, width / 100));
        int rows = (ItemCatalog.entries().size() + columns - 1) / columns;
        return Math.max(8, (height - rows * (BUTTON_HEIGHT + GAP) - 74) / 2);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, KuTheme.BACKDROP);
        int top = panelTop();
        KuTheme.drawPanel(context, 6, top, width - 12, height - top * 2, KuTheme.GOLD);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
