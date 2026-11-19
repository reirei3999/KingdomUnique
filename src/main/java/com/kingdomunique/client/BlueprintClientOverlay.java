package com.kingdomunique.client;

import com.kingdomunique.building.BlueprintBlock;
import com.kingdomunique.network.BlueprintInfoPayload;
import com.kingdomunique.network.RequestBlueprintInfoPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** 設計図の建材リストHUDと建築範囲の3Dプレビュー。 */
@Environment(EnvType.CLIENT)
public final class BlueprintClientOverlay {

    private static BlockPos focusPos = null;
    private static String buildingName = "";
    private static int radius = 9;
    private static List<String> lines = new ArrayList<>();
    private static boolean holdingBlueprint = false;
    private static long lastRequest = 0L;

    private BlueprintClientOverlay() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(BlueprintInfoPayload.ID, (payload, context) -> {
            if (focusPos != null && focusPos.equals(payload.pos())) {
                buildingName = payload.buildingName();
                radius = payload.radius();
                lines = new ArrayList<>(payload.lines());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(BlueprintClientOverlay::tick);
        HudRenderCallback.EVENT.register(BlueprintClientOverlay::renderHud);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (focusPos == null || context.matrixStack() == null || context.consumers() == null) {
                return;
            }
            boolean valid = holdingBlueprint
                    || context.world().getBlockState(focusPos).getBlock() instanceof BlueprintBlock;
            if (!valid) {
                return;
            }
            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
            double minX = focusPos.getX() - radius;
            double minY = focusPos.getY();
            double minZ = focusPos.getZ() - radius;
            WorldRenderer.drawBox(matrices, consumer,
                    new Box(minX, minY, minZ,
                            minX + radius * 2 + 2, minY + 10, minZ + radius * 2 + 2),
                    0.30F, 0.85F, 1.0F, 0.85F);
        });
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            clear();
            return;
        }
        ItemStack held = client.player.getMainHandStack();
        holdingBlueprint = !held.isEmpty() && held.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof BlueprintBlock;

        BlockPos newFocus = null;
        if (client.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos hitPos = hit.getBlockPos();
            if (client.world.getBlockState(hitPos).getBlock() instanceof BlueprintBlock) {
                newFocus = hitPos;
            } else if (holdingBlueprint) {
                newFocus = hitPos.up();
            }
        }
        if (holdingBlueprint && newFocus == null) {
            Vec3d look = client.player.getRotationVec(1.0F);
            newFocus = client.player.getBlockPos().add(
                    (int) Math.round(look.x * 6.0D), 0, (int) Math.round(look.z * 6.0D));
        }
        if (newFocus == null || !newFocus.equals(focusPos)) {
            focusPos = newFocus;
            buildingName = "";
            radius = 9;
            lines = new ArrayList<>();
        }
        if (focusPos != null && client.world.getBlockState(focusPos).getBlock() instanceof BlueprintBlock
                && client.world.getTime() - lastRequest >= 10) {
            lastRequest = client.world.getTime();
            if (ClientPlayNetworking.canSend(RequestBlueprintInfoPayload.ID)) {
                ClientPlayNetworking.send(new RequestBlueprintInfoPayload(focusPos));
            }
        }
    }

    private static void clear() {
        focusPos = null;
        lines = new ArrayList<>();
        buildingName = "";
        holdingBlueprint = false;
    }

    private static void renderHud(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (focusPos == null || lines.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        int y = client.getWindow().getScaledHeight() / 2 + 32;
        int x = 12;
        context.fill(x - 4, y - 16, x + 200, y + lines.size() * 12 + 6, 0x99000000);
        context.drawTextWithShadow(client.textRenderer,
                Text.literal("§6§l" + (buildingName.isEmpty() ? "建築予定" : buildingName)), x, y - 14, 0xFFFFFFFF);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = line.contains("建築中") ? 0xFF55FF55
                    : (line.matches(".*\\s\\d+/\\d+") ? parseColor(line) : 0xFFFFFFFF);
            context.drawTextWithShadow(client.textRenderer, Text.literal(line), x, y + i * 12, color);
        }
    }

    private static int parseColor(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            return 0xFFFFFFFF;
        }
        String[] nums = parts[parts.length - 1].split("/");
        if (nums.length == 2) {
            try {
                return Integer.parseInt(nums[0]) >= Integer.parseInt(nums[1])
                        ? 0xFF55FF55 : 0xFFFF6666;
            } catch (NumberFormatException ignored) {
                return 0xFFFFFFFF;
            }
        }
        return 0xFFFFFFFF;
    }
}
