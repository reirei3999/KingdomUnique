package com.kingdomunique.client;

import com.kingdomunique.network.BuildingBoxesSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockBox;

import java.util.Collections;
import java.util.List;

/** サーバーから同期された建築物の占有範囲を保持する（重複プレビュー判定用）。 */
@Environment(EnvType.CLIENT)
public final class ClientBuildingBoxCache {

    private static volatile List<BlockBox> occupiedBoxes = Collections.emptyList();

    private ClientBuildingBoxCache() {
    }

    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(BuildingBoxesSyncPayload.ID, (payload, context) -> {
            List<BlockBox> boxes = List.copyOf(payload.occupiedBoxes());
            context.client().execute(() -> occupiedBoxes = boxes);
        });
    }

    public static void clear() {
        occupiedBoxes = Collections.emptyList();
    }

    /** BuildingValidator と同じく、周囲1マス余白を含めて既存／建築中と重複するか判定する。 */
    public static boolean intersectsExistingBuilding(BlockBox candidateBox) {
        List<BlockBox> boxes = occupiedBoxes;
        if (boxes.isEmpty()) {
            return false;
        }
        BlockBox padded = new BlockBox(
                candidateBox.getMinX() - 1, candidateBox.getMinY(), candidateBox.getMinZ() - 1,
                candidateBox.getMaxX() + 1, candidateBox.getMaxY(), candidateBox.getMaxZ() + 1);
        for (BlockBox existing : boxes) {
            if (padded.intersects(existing)) {
                return true;
            }
        }
        return false;
    }
}
