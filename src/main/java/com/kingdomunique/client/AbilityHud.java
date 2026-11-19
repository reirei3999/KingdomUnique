package com.kingdomunique.client;

import com.kingdomunique.network.SyncPlayerStatusPayload;
import com.kingdomunique.registry.AbilityDescriptions;
import com.kingdomunique.registry.ContentRegistry;
import com.kingdomunique.registry.UniqueContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** 右下に現在の能力・クールタイムを常時表示するHUD。 */
@Environment(EnvType.CLIENT)
public final class AbilityHud {

    private AbilityHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(AbilityHud::render);
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }
        SyncPlayerStatusPayload status = StatusClient.latest();
        String abilityId = status.abilityId();
        if (abilityId == null || abilityId.isEmpty()) {
            return;
        }

        int x = client.getWindow().getScaledWidth() - 176;
        int y = client.getWindow().getScaledHeight() - 62;
        context.fill(x - 4, y - 4, x + 172, y + 52, 0x99000000);

        ItemStack icon = SelectionIcons.iconFor(abilityId);
        if (!icon.isEmpty()) {
            context.drawItem(icon, x, y);
        }
        context.drawTextWithShadow(client.textRenderer,
                Text.literal(ContentRegistry.nameOf(abilityId)).formatted(net.minecraft.util.Formatting.BOLD),
                x + 22, y + 2, 0xFFFFFFFF);

        int ct = status.cooldownTicks();
        if (AbilityDescriptions.cooldownTicks(abilityId) > 0) {
            if (ct > 0) {
                float seconds = ct / 20.0F;
                int color = ct <= 40 ? 0xFFFF5555 : 0xFFFFDD55;
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal("CT：残り " + String.format("%.1f", seconds) + "秒"),
                        x + 22, y + 15, color);
            } else {
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal("Rキー：発動可能"), x + 22, y + 15, 0xFF55FF55);
            }
        } else {
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(AbilityDescriptions.controlHint(abilityId)),
                    x + 22, y + 15, 0xFFAAAAAA);
        }

        if (UniqueContentRegistry.DIVINE_SMITH.equals(abilityId)) {
            int range = Math.max(1, status.miningRange());
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("採掘範囲: " + range + "x" + range + "（最大3x3）"),
                    x + 22, y + 28, 0xFF55FFFF);
        }
    }
}
