package com.kingdomunique.client;

import com.kingdomunique.building.BlueprintBlock;
import com.kingdomunique.building.BuildingType;
import com.kingdomunique.network.SyncPlayerStatusPayload;
import com.kingdomunique.registry.AbilityDescriptions;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 王国情報・能力状態・設計図資材を1つにまとめた統合HUD（Gemini 提示を既存クラスへ接続）。
 * 左上のコンパクトバナー、ホットバー右隣の能力スロット、左下の状況別資材パネルを描画する。
 */
@Environment(EnvType.CLIENT)
public final class KingdomUnifiedHud {

    private KingdomUnifiedHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(KingdomUnifiedHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        // F1（HUD非表示）中は独自HUDも描画しない。画面を開いている時も視界を邪魔しない。
        if (client.player == null || client.world == null || client.currentScreen != null
                || client.options.hudHidden) {
            return;
        }
        TextRenderer tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        renderTopLeftBanner(context, tr, 8, 8);
        renderAbilitySlot(context, tr, sw, sh);
        renderMaterialPanel(context, tr, client.player, sh);
    }

    /** ① 左上: 覚醒能力・採掘範囲・保有ユニーク数。 */
    private static void renderTopLeftBanner(DrawContext context, TextRenderer tr, int x, int y) {
        SyncPlayerStatusPayload status = StatusClient.latest();
        String abilityId = status.abilityId();
        String name = abilityId == null || abilityId.isEmpty()
                ? "未覚醒" : ContentRegistry.nameOf(abilityId);
        int range = status.miningRange();
        String rangeLabel = range > 1 ? " §b[" + range + "x" + range + "]" : "";
        String text = "§6✦ §f" + name + rangeLabel + " §7| 保有:" + status.ownedIds().size();

        int width = tr.getWidth(text) + 12;
        context.fill(x, y, x + width, y + 16, 0x9010141E);
        context.fill(x, y, x + 2, y + 16, 0xFFE5B84B);
        context.drawTextWithShadow(tr, Text.literal(text), x + 6, y + 3, 0xFFFFFF);
    }

    /** ② ホットバー右隣: 能力アイコン＋CTゲージ（使用可能時は金色枠）。 */
    private static void renderAbilitySlot(DrawContext context, TextRenderer tr, int sw, int sh) {
        SyncPlayerStatusPayload status = StatusClient.latest();
        String abilityId = status.abilityId();
        if (abilityId == null || abilityId.isEmpty()) {
            return;
        }
        int maxCt = AbilityDescriptions.cooldownTicks(abilityId);
        int ct = Math.max(0, status.cooldownTicks());
        boolean ready = maxCt <= 0 || ct <= 0;

        int slotX = sw / 2 + 98;
        int slotY = sh - 22;
        int size = 22;
        int border = ready ? 0xFFE5B84B : 0xFF4A5568;

        context.fill(slotX, slotY, slotX + size, slotY + size, 0xB00D1117);
        drawBorder(context, slotX, slotY, size, size, border);

        ItemStack icon = SelectionIcons.iconFor(abilityId);
        if (!icon.isEmpty()) {
            context.drawItem(icon, slotX + 3, slotY + 3);
        }

        if (maxCt > 0 && ct > 0) {
            float ratio = Math.min(1.0f, (float) ct / maxCt);
            int maskHeight = (int) ((size - 2) * ratio);
            context.fill(slotX + 1, slotY + 1, slotX + size - 1, slotY + 1 + maskHeight, 0xA0000000);
            int barWidth = (int) ((size - 2) * (1.0f - ratio));
            context.fill(slotX + 1, slotY + size - 3, slotX + 1 + barWidth, slotY + size - 1, 0xFF38BDF8);
            String seconds = AbilityDescriptions.formatSeconds(ct);
            int textWidth = tr.getWidth(seconds);
            context.drawTextWithShadow(tr, Text.literal(seconds),
                    slotX + (size - textWidth) / 2, slotY + 7, 0xFFFFFF);
        }

        // [R] キーバッジを小さく表示。
        context.getMatrices().push();
        context.getMatrices().scale(0.75f, 0.75f, 1.0f);
        int badgeX = (int) ((slotX + size - 8) / 0.75f);
        int badgeY = (int) ((slotY - 3) / 0.75f);
        context.drawTextWithShadow(tr, Text.literal("§e[R]"), badgeX, badgeY, 0xFFFFFF);
        context.getMatrices().pop();

        if (maxCt <= 0) {
            // パッシブ能力は操作ヒントをスロット下に小さく出す。
            String hint = AbilityDescriptions.controlHint(abilityId);
            context.drawTextWithShadow(tr, Text.literal("§7" + hint),
                    slotX - Math.max(0, tr.getWidth(hint) - size) / 2, slotY + size + 2, 0xAAAAAA);
        }
    }

    /** ③ 左下: 配置済み設計図を視ている時は投入状況、設計図を持っている時は所持資材を表示。 */
    private static void renderMaterialPanel(DrawContext context, TextRenderer tr,
                                            ClientPlayerEntity player, int screenHeight) {
        List<String> lines = new ArrayList<>();
        String title = null;

        BlockPos focus = BlueprintClientOverlay.getFocusPos();
        if (focus != null && player.getWorld().getBlockState(focus).getBlock() instanceof BlueprintBlock) {
            List<String> serverLines = BlueprintClientOverlay.getLines();
            if (!serverLines.isEmpty()) {
                title = "§b📐 " + BlueprintClientOverlay.getBuildingName();
                lines.addAll(serverLines);
            }
        }

        if (title == null) {
            BuildingType held = getHeldBlueprint(player);
            if (held != null) {
                title = "§b📐 " + held.getDisplayName() + " §7必要資材";
                for (var entry : held.materials().entrySet()) {
                    int owned = countItem(player, entry.getKey());
                    lines.add(entry.getKey().getName().getString() + " " + owned + "/" + entry.getValue());
                }
            }
        }
        if (title == null || lines.isEmpty()) {
            return;
        }

        int rowHeight = 14;
        int headerHeight = 18;
        int panelWidth = 168;
        int panelHeight = headerHeight + lines.size() * rowHeight + 6;
        int topY = screenHeight - 8 - panelHeight;
        int x = 8;

        context.fill(x, topY, x + panelWidth, topY + panelHeight, 0xA8111827);
        drawBorder(context, x, topY, panelWidth, panelHeight, 0xFF3B82F6);
        context.drawTextWithShadow(tr, Text.literal(title), x + 6, topY + 4, 0xFFFFFF);

        int y = topY + headerHeight;
        for (String line : lines) {
            int color = 0xFFFFFFFF;
            if (line.matches(".*\\s\\d+/\\d+.*")) {
                String[] parts = line.split("\\s+");
                String last = parts[parts.length - 1];
                String[] nums = last.split("/");
                if (nums.length == 2) {
                    try {
                        int have = Integer.parseInt(nums[0].replaceAll("[^0-9]", ""));
                        int need = Integer.parseInt(nums[1].replaceAll("[^0-9]", ""));
                        color = have >= need ? 0xFF55FF55 : 0xFFFF6666;
                    } catch (NumberFormatException ignored) {
                        // 数値解析できない行は既定色。
                    }
                }
            }
            context.drawTextWithShadow(tr, Text.literal(line), x + 6, y, color);
            y += rowHeight;
        }
    }

    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int countItem(ClientPlayerEntity player, Item target) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(target)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static BuildingType getHeldBlueprint(ClientPlayerEntity player) {
        BuildingType main = resolveBlueprint(player.getMainHandStack());
        return main != null ? main : resolveBlueprint(player.getOffHandStack());
    }

    private static BuildingType resolveBlueprint(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof BlueprintBlock bp) {
            return bp.getType();
        }
        return null;
    }
}
