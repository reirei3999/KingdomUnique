package com.kingdomunique.client;

import com.kingdomunique.building.BlueprintBlock;
import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.StructureRotationUtil;
import com.kingdomunique.network.BuildingBoxesSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;

/** 設計図アイテムを持った際のパーティクル枠線プレビュー（Gemini 提示を既存クラスへ接続）。 */
@Environment(EnvType.CLIENT)
public final class BlueprintPreviewRenderer {

    // 緑色パーティクル（建築OK）
    private static final DustParticleEffect VALID_PARTICLE =
            new DustParticleEffect(new Vector3f(0.15f, 0.95f, 0.35f), 1.0f);
    // 赤色パーティクル（建築不可：高度外など）
    private static final DustParticleEffect INVALID_PARTICLE =
            new DustParticleEffect(new Vector3f(0.95f, 0.15f, 0.15f), 1.1f);
    // 正面入口マーカー用（水色）
    private static final DustParticleEffect ENTRANCE_PARTICLE =
            new DustParticleEffect(new Vector3f(0.15f, 0.85f, 1.0f), 1.3f);

    private BlueprintPreviewRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BlueprintPreviewRenderer::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }
        // 2tick に1回描画（視認性を保ちつつパーティクル過多を防止）。
        if (world.getTime() % 2L != 0L) {
            return;
        }

        BuildingType type = getHeldBlueprintType(player);
        if (type == null) {
            return;
        }

        HitResult hit = player.raycast(8.0, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos origin = blockHit.getBlockPos().offset(blockHit.getSide());

        // BuildingManager と同じルールで正面入口を決定（プレイヤー側を向く）。
        Direction entranceFacing = player.getHorizontalFacing().getOpposite();
        BlockBox box = StructureRotationUtil.computeBoundingBox(origin, type, entranceFacing);

        // クライアント側バリデーション（高度限界・チャンクロード・既存建築との重複）。
        boolean inBuildLimit = box.getMinY() >= world.getBottomY() + 1
                && box.getMaxY() < world.getTopY()
                && world.isChunkLoaded(box.getMinX() >> 4, box.getMinZ() >> 4)
                && world.isChunkLoaded(box.getMaxX() >> 4, box.getMaxZ() >> 4);
        BuildingBoxesSyncPayload snapshot = ClientBuildingBoxCache.snapshot(world);
        boolean overlapping = snapshot != null && ClientBuildingBoxCache.intersectsExistingBuilding(snapshot, box);
        boolean restricted = snapshot != null && snapshot.restrictedTypes().contains(type);
        boolean isValid = inBuildLimit && snapshot != null && !overlapping && !restricted;

        DustParticleEffect frameColor = isValid ? VALID_PARTICLE : INVALID_PARTICLE;
        renderBoundingBoxParticles(world, box, frameColor);
        renderEntranceIndicator(world, box, entranceFacing);

        int width = box.getMaxX() - box.getMinX() + 1;
        int depth = box.getMaxZ() - box.getMinZ() + 1;
        String statusText = !inBuildLimit ? "§c[高度/範囲外]"
                : snapshot == null ? "§7[判定待ち]"
                : restricted ? "§c[王国の建築条件を満たしていません]"
                : overlapping ? "§c[既存建築と重複]" : "§a[建築可能]";
        if (!player.hasVehicle()) {
            player.sendMessage(Text.literal(statusText + " §f" + type.getDisplayName()
                    + " §7(" + width + "x" + depth + " / 入口: " + formatDirectionJa(entranceFacing) + ")"), true);
        }
    }

    /** 手に持っているアイテムから BuildingType を判定する。 */
    private static BuildingType getHeldBlueprintType(ClientPlayerEntity player) {
        BuildingType main = resolveItem(player.getMainHandStack());
        return main != null ? main : resolveItem(player.getOffHandStack());
    }

    /** 設計図ブロックの BlockItem から BuildingType を取り出す。 */
    private static BuildingType resolveItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof BlueprintBlock bp) {
            return bp.getType();
        }
        return null;
    }

    /** 建築バウンディングボックスの底面外周・上面外周・四隅の垂直ラインをパーティクル描画。 */
    private static void renderBoundingBoxParticles(ClientWorld world, BlockBox box, DustParticleEffect color) {
        double minX = box.getMinX();
        double minY = box.getMinY() + 1.05;
        double minZ = box.getMinZ();
        double maxX = box.getMaxX() + 1.0;
        double maxY = box.getMaxY() + 1.0;
        double maxZ = box.getMaxZ() + 1.0;
        double step = 0.5;

        for (double x = minX; x <= maxX; x += step) {
            spawnDot(world, color, x, minY, minZ);
            spawnDot(world, color, x, minY, maxZ);
            spawnDot(world, color, x, maxY, minZ);
            spawnDot(world, color, x, maxY, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            spawnDot(world, color, minX, minY, z);
            spawnDot(world, color, maxX, minY, z);
            spawnDot(world, color, minX, maxY, z);
            spawnDot(world, color, maxX, maxY, z);
        }
        for (double y = minY; y <= maxY; y += 0.75) {
            spawnDot(world, color, minX, y, minZ);
            spawnDot(world, color, maxX, y, minZ);
            spawnDot(world, color, minX, y, maxZ);
            spawnDot(world, color, maxX, y, maxZ);
        }
    }

    /** 正面入口（ドア位置）を水色の光の柱＋矢印で表示。 */
    private static void renderEntranceIndicator(ClientWorld world, BlockBox box, Direction entranceFacing) {
        double centerX = (box.getMinX() + box.getMaxX() + 1.0) / 2.0;
        double centerZ = (box.getMinZ() + box.getMaxZ() + 1.0) / 2.0;
        double baseY = box.getMinY() + 1.1;

        double doorX = switch (entranceFacing) {
            case WEST -> box.getMinX() - 0.3;
            case EAST -> box.getMaxX() + 1.3;
            default -> centerX;
        };
        double doorZ = switch (entranceFacing) {
            case NORTH -> box.getMinZ() - 0.3;
            case SOUTH -> box.getMaxZ() + 1.3;
            default -> centerZ;
        };

        for (double dy = 0.0; dy <= 2.5; dy += 0.4) {
            spawnDot(world, ENTRANCE_PARTICLE, doorX, baseY + dy, doorZ);
        }
        double tipX = doorX + entranceFacing.getOffsetX() * 0.8;
        double tipZ = doorZ + entranceFacing.getOffsetZ() * 0.8;
        world.addParticle(ParticleTypes.END_ROD, tipX, baseY + 0.5, tipZ, 0.0, 0.02, 0.0);
    }

    private static void spawnDot(ClientWorld world, DustParticleEffect effect, double x, double y, double z) {
        world.addParticle(effect, x, y, z, 0.0, 0.0, 0.0);
    }

    private static String formatDirectionJa(Direction dir) {
        return switch (dir) {
            case NORTH -> "北";
            case SOUTH -> "南";
            case EAST -> "東";
            case WEST -> "西";
            default -> dir.getName();
        };
    }
}
