package com.kingdomunique.client;

import com.kingdomunique.network.BuildingBoxesSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockBox;

/** サーバーから同期された建築物の占有範囲を保持する（重複プレビュー判定用）。 */
@Environment(EnvType.CLIENT)
public final class ClientBuildingBoxCache {

    private static BuildingBoxesSyncPayload snapshot;

    private ClientBuildingBoxCache() {
    }

    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(BuildingBoxesSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> snapshot = payload);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> snapshot = null);
    }

    public static BuildingBoxesSyncPayload snapshot(ClientWorld world) {
        return snapshot != null && snapshot.dimension().equals(world.getRegistryKey().getValue()) ? snapshot : null;
    }

    /** BuildingValidator と同じく、周囲1マス余白を含めて既存／建築中と重複するか判定する。 */
    public static boolean intersectsExistingBuilding(BuildingBoxesSyncPayload snapshot, BlockBox candidateBox) {
        BlockBox padded = new BlockBox(
                candidateBox.getMinX() - 1, candidateBox.getMinY(), candidateBox.getMinZ() - 1,
                candidateBox.getMaxX() + 1, candidateBox.getMaxY(), candidateBox.getMaxZ() + 1);
        for (BlockBox existing : snapshot.occupiedBoxes()) {
            if (padded.intersects(existing)) {
                return true;
            }
        }
        return false;
    }
}
