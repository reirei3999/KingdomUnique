package com.kingdomunique.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 英雄召喚の祭壇（9x9 のクォーツ神殿）を下から順に組み立てる。 */
public final class HeroSanctuaryBuilder {

    /** 祭壇の核（ロードストーン）の origin からの相対座標。 */
    public static final BlockPos CORE_OFFSET = new BlockPos(4, 1, 4);

    private HeroSanctuaryBuilder() {
    }

    public record BlockPlacement(BlockPos pos, BlockState state) {
    }

    /** 下から上へ順番に並べた配置リストを返す（建築アニメーション用）。 */
    public static List<BlockPlacement> generatePlacements(BlockPos origin) {
        List<BlockPlacement> list = new ArrayList<>();
        int size = 9;

        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                boolean outer = dx == 0 || dx == size - 1 || dz == 0 || dz == size - 1;
                list.add(new BlockPlacement(origin.add(dx, -1, dz),
                        Blocks.QUARTZ_BRICKS.getDefaultState()));
                if (outer) {
                    list.add(new BlockPlacement(origin.add(dx, 0, dz),
                            Blocks.QUARTZ_SLAB.getDefaultState()
                                    .with(net.minecraft.block.SlabBlock.TYPE, SlabType.BOTTOM)));
                }
            }
        }

        for (int dx = 3; dx <= 5; dx++) {
            for (int dz = 3; dz <= 5; dz++) {
                list.add(new BlockPlacement(origin.add(dx, 0, dz),
                        Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState()));
            }
        }
        list.add(new BlockPlacement(origin.add(4, 0, 4), Blocks.GOLD_BLOCK.getDefaultState()));
        list.add(new BlockPlacement(origin.add(CORE_OFFSET), Blocks.LODESTONE.getDefaultState()));

        for (int px : new int[]{1, 7}) {
            for (int pz : new int[]{1, 7}) {
                for (int dy = 0; dy <= 4; dy++) {
                    list.add(new BlockPlacement(origin.add(px, dy, pz),
                            Blocks.QUARTZ_PILLAR.getDefaultState()));
                }
                list.add(new BlockPlacement(origin.add(px, 5, pz),
                        Blocks.SEA_LANTERN.getDefaultState()));
            }
        }

        list.sort(Comparator.comparingInt(p -> p.pos().getY()));
        return list;
    }

    /** 一括で配置する（建築完了時）。 */
    public static void build(ServerWorld world, BlockPos origin) {
        for (BlockPlacement placement : generatePlacements(origin)) {
            world.setBlockState(placement.pos(), placement.state(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }
}
