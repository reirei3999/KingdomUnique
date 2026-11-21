package com.kingdomunique.building;

import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** 南向き基準の設計図を、プレイヤーの向きに合わせて回転するユーティリティ。 */
public final class StructureRotationUtil {

    private StructureRotationUtil() {
    }

    /**
     * ローカルサイズ（幅X, 高さY, 奥行Z）。正面（入口）はローカル +Z（南）側。
     * 占有範囲は origin を北西角として Y は origin.y-1（床・基礎の段）〜 origin.y+高さ。
     */
    public static int[] getBuildingDimensions(BuildingType type) {
        return switch (type) {
            case HOUSE -> new int[]{9, 8, 9};
            case BARRACKS -> new int[]{11, 9, 11};
            case BLACKSMITH -> new int[]{11, 6, 9};
            case TOWN_HALL -> new int[]{11, 13, 15};
            case WATCHTOWER -> new int[]{5, 12, 5};
            case GATE -> new int[]{9, 7, 5};
            case WALL -> new int[]{7, 5, 3};
            case HERO_SANCTUARY -> new int[]{9, 7, 9};
            case GRANARY -> new int[]{9, 7, 9};
            case INFIRMARY -> new int[]{9, 7, 7};
            case MARKET -> new int[]{9, 6, 7};
            case ARSENAL -> new int[]{9, 6, 7};
            case TRAINING_GROUND -> new int[]{11, 4, 11};
            case ROYAL_PALACE -> new int[]{13, 14, 15};
        };
    }

    /** 正面を SOUTH 基準とした回転。 */
    public static BlockRotation getRotationFromSouth(Direction facing) {
        return switch (facing) {
            case WEST -> BlockRotation.CLOCKWISE_90;
            case NORTH -> BlockRotation.CLOCKWISE_180;
            case EAST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** ローカル相対座標を回転してワールド座標へ。 */
    public static BlockPos transformPos(BlockPos origin, int dx, int dy, int dz,
                                        int sizeX, int sizeZ, Direction facing) {
        return switch (facing) {
            case WEST -> origin.add(sizeZ - 1 - dz, dy, dx);
            case NORTH -> origin.add(sizeX - 1 - dx, dy, sizeZ - 1 - dz);
            case EAST -> origin.add(dz, dy, sizeX - 1 - dx);
            default -> origin.add(dx, dy, dz);
        };
    }

    /** ブロックの向きを回転する。 */
    public static BlockState transformState(BlockState state, Direction facing) {
        BlockRotation rotation = getRotationFromSouth(facing);
        return rotation == BlockRotation.NONE ? state : state.rotate(rotation);
    }

    /** 回転後の占有範囲。 */
    public static BlockBox computeBoundingBox(BlockPos origin, BuildingType type, Direction facing) {
        int[] dims = getBuildingDimensions(type);
        boolean swapped = facing == Direction.EAST || facing == Direction.WEST;
        int width = swapped ? dims[2] : dims[0];
        int depth = swapped ? dims[0] : dims[2];
        return new BlockBox(origin.getX(), origin.getY() - 1, origin.getZ(),
                origin.getX() + width - 1, origin.getY() + dims[1], origin.getZ() + depth - 1);
    }
}
