package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.client.ui.KuButton;
import com.kingdomunique.network.SyncPlayerStatusPayload;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

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
    private int listOffset;
    private int detailScroll;
    private int detailMaxScroll;

    private int listWidth() {
        return Math.min(132, (Math.min(420, width - 24) - 32) / 3);
    }

    private int visibleEntries() {
        return Math.min(displayIds.size(), Math.max(1, (Math.min(260, height - 24) - 102) / 36));
    }

    private static int categoryAccent(ContentRegistry.Kind kind) {
        if (kind == null) return KuTheme.MUTED;
        return switch (kind) {
            case ABILITY -> KuTheme.GOLD;
            case RELIC -> KuTheme.CYAN;
            case CURSE -> KuTheme.PURPLE;
        };
    }

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

        int listW = listWidth();
        int maxButtons = visibleEntries();
        listOffset = Math.clamp(listOffset, 0, displayIds.size() - maxButtons);
        for (int i = 0; i < maxButtons; i++) {
            final int idx = listOffset + i;
            String id = displayIds.get(idx);
            UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(id);
            Text label = Text.empty();
            if (!id.isEmpty() && id.equals(activeAbilityId)) {
                label = Text.literal("★ ").withColor(KuTheme.GOLD);
            }
            label = label.copy().append(info.name());
            this.addDrawableChild(KuButton.themed(
                    label,
                    btn -> {
                        this.selectedIndex = idx;
                        detailScroll = 0;
                    })
                    .accent(categoryAccent(ContentRegistry.kindOf(id)))
                    .card(id.isEmpty() ? info.icon() : SelectionIcons.iconFor(id),
                            Text.literal("[" + info.category() + "]"), () -> selectedIndex == idx)
                    .dimensions(left + 8, top + 30 + i * 36, listW, 32).build());
        }

        this.addDrawableChild(KuButton.themed(
                Text.literal("閉じる (K / ESC)"), btn -> this.close())
                .dimensions(left + panelW - 112, top + panelH - 26, 104, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, KuTheme.BACKDROP);
        int w = Math.min(420, width - 24);
        int h = Math.min(260, height - 24);
        KuTheme.drawPanel(context, (width - w) / 2, (height - h) / 2, w, h, KuTheme.GOLD);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int panelW = Math.min(420, this.width - 24);
        int panelH = Math.min(260, this.height - 24);
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§6§l能力・遺物・呪物 詳細"),
                left + 10, top + 8, KuTheme.PRIMARY);

        int dividerX = left + listWidth() + 14;
        context.fill(dividerX, top + 28, dividerX + 1, top + panelH - 8, KuTheme.withAlpha(KuTheme.MUTED, 0x60));
        drawSummary(context, left + 8, top + 30 + visibleEntries() * 36 + 6,
                listWidth(), panelH - 66 - visibleEntries() * 36);

        String selectedId = displayIds.get(Math.min(selectedIndex, displayIds.size() - 1));
        UniqueLoreRegistry.UniqueInfo info = UniqueLoreRegistry.getOrFallback(selectedId);

        int detailX = dividerX + 10;
        int detailY = top + 32;
        int detailW = (left + panelW) - detailX - 16;
        int detailTop = detailY;
        int detailBottom = top + panelH - 42;
        detailScroll = Math.clamp(detailScroll, 0, detailMaxScroll);
        context.enableScissor(detailX, detailTop, detailX + detailW, detailBottom);
        detailY -= detailScroll;

        context.drawItem(info.icon(), detailX, detailY);
        int headingY = drawWrapped(context, Text.literal("§e§l" + info.name()),
                detailX + 22, detailY, detailW - 22, KuTheme.PRIMARY);
        headingY = drawWrapped(context, Text.literal("[" + info.category() + "]"),
                detailX + 22, headingY, detailW - 22, categoryAccent(ContentRegistry.kindOf(selectedId)));
        detailY = Math.max(detailY + 26, headingY + 4);
        detailY = drawWrapped(context, Text.literal("§7発動・条件: §f" + info.triggerInfo()),
                detailX, detailY, detailW, KuTheme.PRIMARY) + 2;

        if (selectedId.equals(activeAbilityId) && !selectedId.isEmpty()) {
            String ct = cooldownTicks <= 0 ? "§a使用可能 (READY)"
                    : String.format("§cクールタイム中 (残り %.1f秒)", cooldownTicks / 20.0D);
            String range = miningRange > 1 ? " §7/ §b採掘範囲: " + miningRange + "x" + miningRange : "";
            detailY = drawWrapped(context, Text.literal("§7状態: " + ct + range),
                    detailX, detailY, detailW, KuTheme.PRIMARY) + 3;
        }

        context.fill(detailX, detailY, detailX + detailW, detailY + 1, KuTheme.withAlpha(KuTheme.PRIMARY, 0x40));
        detailY += 6;

        for (String line : info.descriptionLines()) {
            detailY = drawWrapped(context, Text.literal(line), detailX, detailY, detailW, KuTheme.BODY) + 2;
        }
        context.disableScissor();
        detailMaxScroll = Math.max(0, detailY + detailScroll - detailBottom);
        if (detailMaxScroll > 0) {
            int trackH = detailBottom - detailTop;
            int thumbH = Math.max(8, trackH * trackH / (trackH + detailMaxScroll));
            int thumbY = detailTop + (trackH - thumbH) * detailScroll / detailMaxScroll;
            context.fill(detailX + detailW + 4, detailTop, detailX + detailW + 6, detailBottom, KuTheme.BORDER);
            context.fill(detailX + detailW + 4, thumbY, detailX + detailW + 6, thumbY + thumbH, KuTheme.GOLD);
            drawWrapped(context, Text.literal("↑↓ / PgUp・PgDn"), detailX,
                    detailBottom + 2, detailW, KuTheme.MUTED);
        }
    }

    private int drawWrapped(DrawContext context, Text text, int x, int y, int width, int color) {
        for (OrderedText line : textRenderer.wrapLines(text, width)) {
            context.drawTextWithShadow(textRenderer, line, x, y, color);
            y += 11;
        }
        return y;
    }

    private void drawSummary(DrawContext context, int x, int y, int w, int h) {
        if (h < 20) return;
        KuTheme.drawSlot(context, x, y, w, h);
        context.enableScissor(x + 4, y + 4, x + w - 4, y + h - 4);
        context.drawTextWithShadow(textRenderer, Text.literal("所持 " + countOwned() + "種"),
                x + 6, y + 8, KuTheme.PRIMARY);
        if (h < 110) {
            context.disableScissor();
            return;
        }
        int lineY = y + 26;
        for (ContentRegistry.Kind kind : ContentRegistry.Kind.values()) {
            long count = displayIds.stream().filter(id -> ContentRegistry.kindOf(id) == kind).count();
            Text label = Text.literal(kind.getJapanese() + " " + count).withColor(categoryAccent(kind));
            context.drawTextWithShadow(textRenderer, label, x + 6, lineY, KuTheme.PRIMARY);
            lineY += 14;
        }
        Text active = Text.literal("発動中: " + UniqueLoreRegistry.getOrFallback(activeAbilityId).name());
        context.drawTextWithShadow(textRenderer, KuButton.fitLabel(textRenderer, active, w - 12),
                x + 6, lineY + 4, KuTheme.SECONDARY);
        int hintY = lineY + 22;
        var hints = textRenderer.wrapLines(Text.literal("カードをクリックで詳細 / ホイールで一覧"), w - 12);
        if (hintY + hints.size() * 11 <= y + h - 6) {
            for (OrderedText line : hints) {
                context.drawTextWithShadow(textRenderer, line, x + 6, hintY, KuTheme.MUTED);
                hintY += 11;
            }
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = (width - Math.min(420, width - 24)) / 2;
        int top = (height - Math.min(260, height - 24)) / 2;
        if (mouseX >= left + 8 && mouseX < left + 8 + listWidth() && mouseY >= top + 30
                && mouseY < top + 30 + visibleEntries() * 36 && verticalAmount != 0) {
            int offset = Math.clamp(listOffset + (verticalAmount > 0 ? -1 : 1),
                    0, displayIds.size() - visibleEntries());
            if (offset != listOffset) {
                listOffset = offset;
                clearAndInit();
            }
            return true;
        }
        if (mouseX >= left + listWidth() + 24 && mouseX < width - left - 8
                && mouseY >= top + 30 && mouseY < height - top - 42 && verticalAmount != 0) {
            detailScroll = Math.clamp(detailScroll + (verticalAmount > 0 ? -22 : 22), 0, detailMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        int page = Math.max(11, Math.min(260, height - 24) - 85);
        int scroll = switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> -11;
            case GLFW.GLFW_KEY_DOWN -> 11;
            case GLFW.GLFW_KEY_PAGE_UP -> -page;
            case GLFW.GLFW_KEY_PAGE_DOWN -> page;
            default -> 0;
        };
        if (scroll != 0) {
            detailScroll = Math.clamp(detailScroll + scroll, 0, detailMaxScroll);
            return true;
        }
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
}
