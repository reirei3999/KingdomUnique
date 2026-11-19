package com.kingdomunique.ability;

/** 現場監督の「設計図建築」用に予約された倍率メソッド群。 */
public final class SiteSupervisorUtil {

    /** 建築時間の短縮倍率（1.0 で通常、0.6 で40%短縮）。 */
    public static final double BUILD_TIME_MULTIPLIER = 0.6D;

    /** 必要資材コストの削減倍率（1.0 で通常、0.7 で30%削減）。 */
    public static final double MATERIAL_COST_MULTIPLIER = 0.7D;

    private SiteSupervisorUtil() {
    }

    /** 建築時間短縮倍率を返す（ステップ4の設計図建築から利用する）。 */
    public static double getBuildTimeMultiplier() {
        return BUILD_TIME_MULTIPLIER;
    }

    /** 必要資材コスト倍率を返す（ステップ4の設計図建築から利用する）。 */
    public static double getMaterialCostMultiplier() {
        return MATERIAL_COST_MULTIPLIER;
    }

    /** 指定Tick数に建築時間倍率を適用した値を返す。 */
    public static int applyBuildTime(int baseTicks) {
        return Math.max(1, (int) Math.round(baseTicks * BUILD_TIME_MULTIPLIER));
    }

    /** 指定資材数にコスト倍率を適用した値を返す。 */
    public static int applyMaterialCost(int baseCount) {
        return Math.max(1, (int) Math.round(baseCount * MATERIAL_COST_MULTIPLIER));
    }
}
