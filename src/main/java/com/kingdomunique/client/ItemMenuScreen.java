package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuButton;
import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.item.ItemCatalog;
import com.kingdomunique.network.RequestItemPayload;
import com.kingdomunique.network.ItemGrantResultPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** 専用アイテムを一覧から取得するメニュー。 */
@Environment(EnvType.CLIENT)
public final class ItemMenuScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;
    private static long nextRequestId;
    private int page;
    private String pendingItem;
    private long pendingRequestId;
    private int pendingTicks;
    private Text result = Text.literal("アイテムを選んでください");
    private int resultColor = KuTheme.MUTED;

    private int resultHeight() {
        return textRenderer.wrapLines(result, Math.max(1, width - 40)).size() * (textRenderer.fontHeight + 2) + 12;
    }

    private void requestItem(String id) {
        if (pendingItem != null) {
            return;
        }
        pendingItem = id;
        pendingRequestId = ++nextRequestId;
        pendingTicks = 0;
        result = Text.literal("付与中…");
        resultColor = KuTheme.MUTED;
        clearAndInit();
        ClientPlayNetworking.send(new RequestItemPayload(id, pendingRequestId));
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingItem != null && ++pendingTicks >= 100) {
            pendingItem = null;
            result = Text.literal("応答がありません。もう一度クリックしてください");
            resultColor = KuTheme.DRAWBACK;
            clearAndInit();
        }
    }

    public void acceptResult(ItemGrantResultPayload payload) {
        if (!payload.itemId().equals(pendingItem) || payload.requestId() != pendingRequestId) {
            return;
        }
        pendingItem = null;
        result = Text.literal(payload.success()
                ? payload.itemName() + " ×" + payload.quantity() + " を入手" : payload.reason());
        resultColor = payload.success() ? KuTheme.MERIT : KuTheme.DRAWBACK;
        if (payload.success() && client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F));
        }
        clearAndInit();
    }

    public ItemMenuScreen() {
        super(Text.literal("専用アイテム"));
    }

    private record Layout(int columns, int rows, int buttonWidth, int top, int height) {
        int pageSize() {
            return columns * rows;
        }
    }

    private Layout layout() {
        int available = Math.max(1, width - 24);
        int longest = ItemCatalog.entries().stream().mapToInt(entry -> textRenderer.getWidth(entry.label()))
                .max().orElse(0);
        int maxColumns = Math.max(1, Math.min(5, (available + GAP) / (56 + GAP)));
        int preferred = Math.max(1, Math.min(maxColumns, (available + GAP) / (longest + 12 + GAP)));
        int maxRows = Math.max(1, (height - 78 - resultHeight()) / (BUTTON_HEIGHT + GAP));
        int required = (ItemCatalog.entries().size() + maxRows - 1) / maxRows;
        int columns = Math.min(maxColumns, Math.max(preferred, required));
        int rows = Math.max(1, Math.min(maxRows, (ItemCatalog.entries().size() + columns - 1) / columns));
        int panelHeight = rows * (BUTTON_HEIGHT + GAP) + 66 + resultHeight();
        return new Layout(columns, rows, Math.max(1, (available - (columns - 1) * GAP) / columns),
                Math.max(6, (height - panelHeight) / 2), panelHeight);
    }

    @Override
    protected void init() {
        Layout layout = layout();
        var entries = ItemCatalog.entries();
        int pages = Math.max(1, (entries.size() + layout.pageSize() - 1) / layout.pageSize());
        page = Math.clamp(page, 0, pages - 1);
        int totalWidth = layout.columns() * layout.buttonWidth() + (layout.columns() - 1) * GAP;
        int startX = (width - totalWidth) / 2;
        int startY = layout.top() + 40;
        for (int i = 0; i < layout.pageSize() && page * layout.pageSize() + i < entries.size(); i++) {
            ItemCatalog.Entry entry = entries.get(page * layout.pageSize() + i);
            int x = startX + (i % layout.columns()) * (layout.buttonWidth() + GAP);
            int y = startY + (i / layout.columns()) * (BUTTON_HEIGHT + GAP);
            var itemButton = addDrawableChild(KuButton.themed(Text.literal(entry.label()),
                    button -> requestItem(entry.id()))
                    .dimensions(x, y, layout.buttonWidth(), BUTTON_HEIGHT).build());
            itemButton.active = pendingItem == null;
        }
        int footerY = startY + layout.rows() * (BUTTON_HEIGHT + GAP) + resultHeight() + 2;
        addDrawableChild(KuButton.themed(Text.literal("閉じる"), button -> close())
                .dimensions(width / 2 - 45, footerY, 90, 18).build());
        if (pages > 1) {
            var previous = addDrawableChild(KuButton.themed(Text.literal("‹ " + (page + 1) + "/" + pages), button -> {
                page--;
                clearAndInit();
            }).dimensions(startX, footerY, 60, 18).build());
            previous.active = page > 0;
            var next = addDrawableChild(KuButton.themed(Text.literal("次へ ›"), button -> {
                page++;
                clearAndInit();
            }).dimensions(startX + totalWidth - 60, footerY, 60, 18).build());
            next.active = page + 1 < pages;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Layout layout = layout();
        KuTheme.drawTitle(context, textRenderer, title, width / 2, layout.top() + 8);
        Text hint = Text.literal("クリックでアイテムを入手（ユニーク武器は世界で一つだけ）");
        context.drawCenteredTextWithShadow(textRenderer, KuButton.fitLabel(textRenderer, hint, width - 32),
                width / 2, layout.top() + 26, KuTheme.MUTED);
        int resultY = layout.top() + 40 + layout.rows() * (BUTTON_HEIGHT + GAP);
        KuTheme.drawSlot(context, 12, resultY, width - 24, resultHeight());
        int y = resultY + 6;
        for (var line : textRenderer.wrapLines(result, Math.max(1, width - 40))) {
            context.drawTextWithShadow(textRenderer, line, 20, y, resultColor);
            y += textRenderer.fontHeight + 2;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, KuTheme.BACKDROP);
        Layout layout = layout();
        KuTheme.drawPanel(context, 6, layout.top(), width - 12, layout.height(), KuTheme.GOLD);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
