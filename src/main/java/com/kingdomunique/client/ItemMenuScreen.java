package com.kingdomunique.client;

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
        int startY = 34;
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
        this.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0x99000000);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFD700);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("クリックでアイテムを入手（ユニーク武器は世界で一つだけ）"),
                this.width / 2, 27, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
