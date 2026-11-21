package com.kingdomunique.client;

import com.kingdomunique.network.SyncPlayerStatusPayload;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力・遺物・呪物の詳細確認画面（Gemini 提示の2ペインUI）。
 * 左のリストをクリックすると右側にアイコン・カテゴリ・発動条件・効果説明を表示する。
 */
@Environment(EnvType.CLIENT)
public final class StatusScreen extends Screen {

    private final List<String> displayIds = new ArrayList<>();
    private final String activeAbilityId;
    private final int cooldownTicks;
    private final int miningRange;
    private int selectedIndex = 0;

    /** キャッシュ済み同期データから開く（Kキー用）。 */
    public StatusScreen() {
        this(StatusClient.latest());
    }

    /** サーバーから届いたペイロードから開く（/ku info 用）。 */
    public StatusScreen(SyncPlayerStatusPayload payload) {
        super(Text.literal("KingdomUnique 能力・遺物・呪物ステータス"));
        this.activeAbilityId = payload.abilityId();
        this.cooldownTicks = payload.cooldownTicks();
        this.miningRange = payload.miningRange();

        // Gemini 修正: ユニーク武器のID（excalibur 等）は確認画面に出さない。
        // kindOf() が null のIDは能力・遺物・呪物ではないため除外する。
        if (activeAbilityId != null && !activeAbilityId.isEmpty()
                && ContentRegistry.kindOf(activeAbilityId) != null) {
            displayIds.add(activeAbilityId);
        }
        for (String id : payload.ownedIds()) {
            if (ContentRegistry.kindOf(id) != null && !displayIds.contains(id)) {
                displayIds.add(id);
            }
        }
        if (displayIds.isEmpty()) {
            displayIds.add("");
        }
    }

    @Override
    protected void init() {
        super.init();

        int panelW = Math.min(420, this.width - 24);
        int panelH = Math.min(260, this.height - 24);
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        int listW = 132;
        int maxButtons = Math.min(displayIds.size(), Math.max(1, (panelH - 56) / 22));
        for (int i = 0; i < maxButtons; i++) {
            final int idx = i;
            UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(displayIds.get(i));
            String prefix = displayIds.get(i).equals(activeAbilityId) ? "§6★ " : "・";
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(prefix + info.name()),
                    btn -> this.selectedIndex = idx)
                    .dimensions(left + 8, top + 30 + i * 22, listW, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("閉じる (K / ESC)"), btn -> this.close())
                .dimensions(left + panelW - 112, top + panelH - 26, 104, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF0B0B10);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelW = Math.min(420, this.width - 24);
        int panelH = Math.min(260, this.height - 24);
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        context.fill(left, top, left + panelW, top + panelH, 0xE60F172A);
        drawBorder(context, left, top, panelW, panelH, 0xFFE5B84B);

        context.fill(left + 1, top + 1, left + panelW - 1, top + 24, 0xFF1E293B);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§6§l✦ KingdomUnique 能力詳細 §r§7(保有: "
                        + ContentRegistry.ALL_IDS.size() + "種 / 所持: " + countOwned() + ")"),
                left + 10, top + 8, 0xFFFFFF);

        int dividerX = left + 146;
        context.fill(dividerX, top + 28, dividerX + 1, top + panelH - 8, 0x6094A3B8);

        String selectedId = displayIds.get(Math.min(selectedIndex, displayIds.size() - 1));
        UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(selectedId);

        int detailX = dividerX + 10;
        int detailY = top + 32;
        int detailW = (left + panelW) - detailX - 10;

        context.drawItem(info.icon(), detailX, detailY);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§e§l" + info.name()),
                detailX + 22, detailY + 1, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§b[" + info.category() + "]"),
                detailX + 22, detailY + 11, 0xFFFFFF);

        detailY += 26;
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§7発動・条件: §f" + info.triggerInfo()), detailX, detailY, 0xFFFFFF);
        detailY += 12;

        if (selectedId.equals(activeAbilityId) && !selectedId.isEmpty()) {
            String ct = cooldownTicks <= 0 ? "§a使用可能 (READY)"
                    : String.format("§cクールタイム中 (残り %.1f秒)", cooldownTicks / 20.0D);
            String range = miningRange > 1 ? " §7/ §b採掘範囲: " + miningRange + "x" + miningRange : "";
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7状態: " + ct + range),
                    detailX, detailY, 0xFFFFFF);
            detailY += 14;
        }

        context.fill(detailX, detailY, detailX + detailW, detailY + 1, 0x40FFFFFF);
        detailY += 6;

        for (String line : info.descriptionLines()) {
            for (OrderedText wrapped : this.textRenderer.wrapLines(Text.literal(line), detailW)) {
                if (detailY > top + panelH - 32) {
                    break;
                }
                context.drawTextWithShadow(this.textRenderer, wrapped, detailX, detailY, 0xE2E8F0);
                detailY += 11;
            }
            detailY += 2;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private int countOwned() {
        int count = 0;
        for (String id : displayIds) {
            if (!id.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Kキー (GLFW_KEY_K = 75) でも閉じられる。
        if (keyCode == 75) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
}
