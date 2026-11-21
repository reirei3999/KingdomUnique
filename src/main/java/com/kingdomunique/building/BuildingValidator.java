package com.kingdomunique.building;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/** 建築開始前のバリデーション（上限・前提・重複・高度・破壊不能ブロック）。 */
public final class BuildingValidator {

    private BuildingValidator() {
    }

    public static Optional<Text> validate(ServerWorld world, BuildData.BuildSite site) {
        BuildingType type = site.type();
        UUID owner = site.owner();
        KingdomBuildingState state = KingdomBuildingState.get(world);

        if ((type == BuildingType.HERO_SANCTUARY || type == BuildingType.TOWN_HALL
                || type == BuildingType.ROYAL_PALACE)
                && state.countBuildings(world, owner, type) - (site.ready() ? 1 : 0) >= 1) {
            return Optional.of(Text.literal("§c[建築不可] " + type.getDisplayName()
                    + " は1王国につき1つまでです。"));
        }

        if (type == BuildingType.ROYAL_PALACE) {
            if (!state.hasCompletedBuilding(owner, BuildingType.TOWN_HALL)) {
                return Optional.of(Text.literal("§c[建築不可] 王宮を建てるには先に王城・役場を完成させる必要があります。"));
            }
            ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
            int population = 0;
            if (overworld != null) {
                com.kingdomunique.kingdom.KingdomData.KingdomInfo info =
                        com.kingdomunique.kingdom.KingdomData.getOrCreate(overworld).get(owner);
                if (info != null) {
                    population = info.population();
                }
            }
            if (population < 30) {
                return Optional.of(Text.literal(
                        "§c[建築不可] 王宮の建設には国民30人以上が必要です（現在: " + population + "人）。"));
            }
        }

        BlockBox box = StructureRotationUtil.computeBoundingBox(site.pos(), type, site.facing());
        if (box.getMinY() < world.getBottomY() + 1 || box.getMaxY() >= world.getTopY()) {
            return Optional.of(Text.literal("§c[建築不可] 建築範囲がワールドの高度限界を超えています。"));
        }

        BlockBox padded = new BlockBox(box.getMinX() - 1, box.getMinY(), box.getMinZ() - 1,
                box.getMaxX() + 1, box.getMaxY(), box.getMaxZ() + 1);
        for (KingdomBuildingState.CompletedBuilding b : state.entries()) {
            if (padded.intersects(b.boundingBox)) {
                return Optional.of(Text.literal("§c[建築不可] 既存の建築物（"
                        + b.type.getDisplayName() + "）と範囲が重なっています。"));
            }
        }
        for (BuildData.BuildSite otherSite : BuildData.getOrCreate(world).sites()) {
            if (otherSite.pos().equals(site.pos())) {
                continue;
            }
            BlockBox other = StructureRotationUtil.computeBoundingBox(
                    otherSite.pos(), otherSite.type(), otherSite.facing());
            if (padded.intersects(other)) {
                return Optional.of(Text.literal("§c[建築不可] 建築中の建物（"
                        + otherSite.type().getDisplayName() + "）と範囲が重なっています。"));
            }
        }

        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    return Optional.of(Text.literal("§c[建築不可] 建築範囲の一部が未ロードのチャンクです。"));
                }
                for (int y = box.getMinY(); y <= box.getMaxY(); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState bs = world.getBlockState(p);
                    if (bs.isOf(Blocks.BEDROCK) || bs.isOf(Blocks.END_PORTAL_FRAME)
                            || bs.isOf(Blocks.COMMAND_BLOCK)) {
                        return Optional.of(Text.literal("§c[建築不可] 範囲内に破壊不能ブロックがあります。"));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
