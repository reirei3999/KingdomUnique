package com.kingdomunique.world;

import net.minecraft.util.math.BlockPos;

/** 英雄召喚の祭壇の定数。形は building.CivicStructures#heroSanctuary が組み立てる。 */
public final class HeroSanctuaryBuilder {

    /** 祭壇の核（ロードストーン）の origin からの相対座標。 */
    public static final BlockPos CORE_OFFSET = new BlockPos(4, 1, 4);

    private HeroSanctuaryBuilder() {
    }
}
