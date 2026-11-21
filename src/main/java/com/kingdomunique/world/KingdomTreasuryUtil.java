package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 王国の倉庫（タウンホール・穀物庫・交易場）の在庫確認・引き落としユーティリティ（Gemini 提示）。 */
public final class KingdomTreasuryUtil {

    private KingdomTreasuryUtil() {
    }

    /** 王国の倉庫施設内の全チェスト・樽 Inventory を取得する。 */
    public static List<Inventory> getKingdomStorages(ServerWorld world, UUID kingUuid) {
        List<Inventory> storages = new ArrayList<>();
        KingdomBuildingState buildings = KingdomBuildingState.get(world);
        for (KingdomBuildingState.CompletedBuilding b : buildings.entries()) {
            if (!b.ownerUuid.equals(kingUuid)) {
                continue;
            }
            if (b.type != BuildingType.TOWN_HALL && b.type != BuildingType.GRANARY
                    && b.type != BuildingType.MARKET && b.type != BuildingType.WAREHOUSE) {
                continue;
            }
            BlockBox box = b.boundingBox;
            for (int x = box.getMinX() - 1; x <= box.getMaxX() + 1; x++) {
                for (int z = box.getMinZ() - 1; z <= box.getMaxZ() + 1; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    for (int y = box.getMinY(); y <= box.getMaxY() + 1; y++) {
                        BlockEntity be = world.getBlockEntity(new BlockPos(x, y, z));
                        if (be instanceof Inventory inv && !storages.contains(inv)) {
                            storages.add(inv);
                        }
                    }
                }
            }
        }
        return storages;
    }

    /** 王国倉庫内にある指定アイテムの合計在庫数を返す（HUD表示用）。 */
    public static int countTreasuryItem(ServerWorld world, UUID kingUuid, Item target) {
        int total = 0;
        for (Inventory inv : getKingdomStorages(world, kingUuid)) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isOf(target)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** 倉庫チェストから優先消費し、不足分だけ王のインベントリから補填する。 */
    public static boolean consumeFromTreasuryOrKing(ServerWorld world, ServerPlayerEntity king,
                                                    Item target, int required) {
        if (required <= 0) {
            return true;
        }
        List<Inventory> storages = getKingdomStorages(world, king.getUuid());
        int storageCount = 0;
        for (Inventory inv : storages) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(target)) {
                    storageCount += s.getCount();
                }
            }
        }
        int playerCount = 0;
        for (int i = 0; i < king.getInventory().size(); i++) {
            ItemStack s = king.getInventory().getStack(i);
            if (s.isOf(target)) {
                playerCount += s.getCount();
            }
        }
        if (storageCount + playerCount < required) {
            return false;
        }

        int remaining = required;
        // 1. まず倉庫チェストから引き落とし。
        for (Inventory inv : storages) {
            boolean dirty = false;
            for (int i = 0; i < inv.size() && remaining > 0; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(target)) {
                    int take = Math.min(remaining, s.getCount());
                    s.decrement(take);
                    remaining -= take;
                    dirty = true;
                }
            }
            if (dirty) {
                inv.markDirty();
            }
            if (remaining <= 0) {
                break;
            }
        }
        // 2. 不足分を王の手持ちから引き落とし。
        if (remaining > 0) {
            for (int i = 0; i < king.getInventory().size() && remaining > 0; i++) {
                ItemStack s = king.getInventory().getStack(i);
                if (s.isOf(target)) {
                    int take = Math.min(remaining, s.getCount());
                    s.decrement(take);
                    remaining -= take;
                }
            }
        }
        return true;
    }
}
