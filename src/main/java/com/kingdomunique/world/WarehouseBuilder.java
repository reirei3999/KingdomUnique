package com.kingdomunique.world;

import com.kingdomunique.building.BuildingType;
import com.kingdomunique.building.KingdomBuildingState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.UUID;

/**
 * 初期村に建つ 7x7 の王国倉庫。完成建築（WAREHOUSE）として登録するため、
 * 中のチェスト・樽は国庫（人口増加の食料・維持費など）として使われる。
 */
public final class WarehouseBuilder {

    public static final int SIZE = 7;
    private static final int WALL_HEIGHT = 4;

    private WarehouseBuilder() {
    }

    /** plot（北西の角・地表の1つ上のY）に倉庫を建て、owner の王国倉庫として登録する。 */
    public static BlockBox build(ServerWorld world, BlockPos plot, UUID owner) {
        generate((pos, state) -> set(world, plot.add(pos), state));
        int last = SIZE - 1;
        for (int dx = 0; dx < SIZE; dx++) {
            for (int dz = 0; dz < SIZE; dz++) {
                for (int dy = -2; dy >= -4; dy--) {
                    BlockPos support = plot.add(dx, dy, dz);
                    if (!world.getBlockState(support).isReplaceable()) {
                        break;
                    }
                    set(world, support, Blocks.COBBLESTONE.getDefaultState());
                }
                for (int dy = WALL_HEIGHT + 1; dy <= WALL_HEIGHT + 2; dy++) {
                    set(world, plot.add(dx, dy, dz), Blocks.AIR.getDefaultState());
                }
            }
        }
        BlockBox box = new BlockBox(plot.getX(), plot.getY() - 1, plot.getZ(),
                plot.getX() + last, plot.getY() + WALL_HEIGHT, plot.getZ() + last);
        if (owner != null) {
            KingdomBuildingState.get(world).add(owner, BuildingType.WAREHOUSE, plot, Direction.SOUTH, box, null);
        }
        return box;
    }

    public static void generate(java.util.function.BiConsumer<BlockPos, BlockState> setter) {
        BlockPos plot = BlockPos.ORIGIN;
        int last = SIZE - 1;
        for (int dx = 0; dx < SIZE; dx++) {
            for (int dz = 0; dz < SIZE; dz++) {
                boolean border = dx == 0 || dx == last || dz == 0 || dz == last;
                setter.accept(plot.add(dx, -1, dz),
                        (border ? Blocks.COBBLESTONE : Blocks.SPRUCE_PLANKS).getDefaultState());

                boolean corner = (dx == 0 || dx == last) && (dz == 0 || dz == last);
                for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                    BlockPos pos = plot.add(dx, dy, dz);
                    BlockState state;
                    if (corner) {
                        state = Blocks.OAK_LOG.getDefaultState();
                    } else if (border) {
                        boolean doorway = dz == last && dx == 3 && dy <= 1;
                        boolean window = dy == 2 && (dx == 3 || dz == 3);
                        state = doorway ? Blocks.AIR.getDefaultState()
                                : window ? Blocks.GLASS_PANE.getDefaultState()
                                : dy == WALL_HEIGHT - 1
                                ? Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,
                                        dx == 0 || dx == last ? Direction.Axis.Z : Direction.Axis.X)
                                : Blocks.SPRUCE_PLANKS.getDefaultState();
                    } else {
                        state = Blocks.AIR.getDefaultState();
                    }
                    setter.accept(pos, state);
                }
                // 平らな屋根と、その上の空間を空ける。
                setter.accept(plot.add(dx, WALL_HEIGHT, dz), Blocks.SPRUCE_SLAB.getDefaultState());
            }
        }

        // 収納：奥の壁沿いにチェスト4つ、左右に樽2つ。
        for (int dx = 1; dx <= 5; dx++) {
            if (dx == 3) {
                continue;
            }
            setter.accept(plot.add(dx, 0, 1), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        }
        setter.accept(plot.add(1, 0, 3), Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.EAST));
        setter.accept(plot.add(5, 0, 3), Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.WEST));
        setter.accept(plot.add(3, 0, 1), Blocks.CRAFTING_TABLE.getDefaultState());
        setter.accept(plot.add(3, 3, 3), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }
}
