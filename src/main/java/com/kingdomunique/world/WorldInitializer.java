package com.kingdomunique.world;

import com.kingdomunique.KingdomUniqueMod;
import com.kingdomunique.data.WorldUniqueData;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ワールド初回生成時の初期化処理。
 *
 *  - オーバーワールドのワールドボーダーを中心(0,0)・5000x5000に設定
 *  - 5000x5000 の範囲内に、適度に離れた10箇所の村を確実に配置
 */
public final class WorldInitializer {

    /** ワールドボーダーの一辺（ブロック数）。 */
    public static final double WORLD_SIZE = 5000.0;

    /** 生成する村の数。 */
    public static final int VILLAGE_COUNT = 10;

    /** 村候補を配置するグリッド（X方向4列、約 -2400 〜 +2400）。 */
    private static final int[] X_CELLS = {-2000, -700, 700, 2000};

    /** 村候補を配置するグリッド（Z方向4列、全16マスから10マスを選択）。 */
    private static final int[] Z_CELLS = {-2000, -700, 700, 2000};

    private static final long SITE_SEED_XOR = 0x4B494E47_444F4DL;

    private WorldInitializer() {
    }

    /** ServerWorldEvents.LOAD から呼ばれる初期化エントリ。 */
    public static void initialize(ServerWorld world) {
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);

        if (data.isInitialized()) {
            KingdomUniqueMod.LOGGER.info("[KingdomUnique] Overworld already initialized. Skipping setup.");
            return;
        }

        configureWorldBorder(world);

        List<WorldUniqueData.VillageSite> sites = planVillageSites(world.getSeed());
        for (WorldUniqueData.VillageSite site : sites) {
            VillageBuilder.build(world, site.x(), site.z());
        }

        data.setVillageSites(sites);
        data.setInitialized(true);
        data.markDirty();

        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Overworld setup done: border {}x{}, villages={}.",
                (int) WORLD_SIZE, (int) WORLD_SIZE, sites.size());
    }

    /** ボーダーを中心(0,0)・5000x5000に設定する。 */
    public static void configureWorldBorder(ServerWorld world) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(WORLD_SIZE);
    }

    /**
     * 12個のグリッド Cell から10個を選び、それぞれにジッターを加えて村座標を決定する。
     * グリッド間隔は約1200ブロック、ジッター±200なので最低でも約800ブロックは離れる。
     */
    private static List<WorldUniqueData.VillageSite> planVillageSites(long worldSeed) {
        Random random = new Random(worldSeed ^ SITE_SEED_XOR);

        int[][] cells = new int[X_CELLS.length * Z_CELLS.length][2];
        int index = 0;
        for (int x : X_CELLS) {
            for (int z : Z_CELLS) {
                cells[index][0] = x;
                cells[index][1] = z;
                index++;
            }
        }

        // グリッドをシャッフルしてから先頭10個を採用（＝12マスのうち2マスは使わない）。
        for (int i = cells.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] tmp = cells[i];
            cells[i] = cells[j];
            cells[j] = tmp;
        }

        List<WorldUniqueData.VillageSite> sites = new ArrayList<>();
        for (int i = 0; i < VILLAGE_COUNT; i++) {
            int x = cells[i][0] + random.nextInt(401) - 200;
            int z = cells[i][1] + random.nextInt(401) - 200;
            sites.add(new WorldUniqueData.VillageSite(x, z));
        }
        return sites;
    }
}
