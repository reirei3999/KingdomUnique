package com.kingdomunique.registry;

import com.kingdomunique.data.WorldUniqueData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界で一つだけ存在する12のユニーク能力。
 *
 * ステップ3で全種に実際の効果を実装したため、ID一覧をこの12種に統一した。
 */
public final class UniqueContentRegistry {

    public enum Category {
        PRODUCTION("生産・内政"),
        COMBAT("戦闘・身体強化"),
        SPECIAL("特殊・空間・支配");

        private final String japanese;

        Category(String japanese) {
            this.japanese = japanese;
        }

        public String getJapanese() {
            return japanese;
        }
    }

    public record UniqueEntry(String id, String displayName, Category category) {
    }

    private static final Map<String, UniqueEntry> ENTRIES;

    static {
        Map<String, UniqueEntry> map = new LinkedHashMap<>();
        map.put("divine_smith", new UniqueEntry("divine_smith", "神匠", Category.PRODUCTION));
        map.put("trickery", new UniqueEntry("trickery", "奇術", Category.PRODUCTION));
        map.put("fertility", new UniqueEntry("fertility", "豊穣", Category.PRODUCTION));
        map.put("site_supervisor", new UniqueEntry("site_supervisor", "現場監督", Category.PRODUCTION));
        map.put("conqueror", new UniqueEntry("conqueror", "覇王", Category.COMBAT));
        map.put("gale", new UniqueEntry("gale", "疾風", Category.COMBAT));
        map.put("indomitable", new UniqueEntry("indomitable", "不屈", Category.COMBAT));
        map.put("spatial_leap", new UniqueEntry("spatial_leap", "空間跳躍", Category.SPECIAL));
        map.put("phase_through", new UniqueEntry("phase_through", "物質透過", Category.SPECIAL));
        map.put("shadow_step", new UniqueEntry("shadow_step", "影渡り", Category.SPECIAL));
        map.put("absolute_dominion", new UniqueEntry("absolute_dominion", "絶対支配", Category.SPECIAL));
        map.put("gravity_control", new UniqueEntry("gravity_control", "重力操作", Category.SPECIAL));
        ENTRIES = Collections.unmodifiableMap(map);
    }

    /** 能力12種のID（UNIQUE_IDS はステップ1からの互換名）。 */
    public static final List<String> ABILITY_IDS = List.copyOf(ENTRIES.keySet());
    public static final List<String> UNIQUE_IDS = ABILITY_IDS;

    /** 能力ID定数（誤記防止用）。 */
    public static final String DIVINE_SMITH = "divine_smith";
    public static final String TRICKERY = "trickery";
    public static final String FERTILITY = "fertility";
    public static final String SITE_SUPERVISOR = "site_supervisor";
    public static final String CONQUEROR = "conqueror";
    public static final String GALE = "gale";
    public static final String INDOMITABLE = "indomitable";
    public static final String SPATIAL_LEAP = "spatial_leap";
    public static final String PHASE_THROUGH = "phase_through";
    public static final String SHADOW_STEP = "shadow_step";
    public static final String ABSOLUTE_DOMINION = "absolute_dominion";
    public static final String GRAVITY_CONTROL = "gravity_control";

    private UniqueContentRegistry() {
    }

    public static UniqueEntry get(String id) {
        return ENTRIES.get(id);
    }

    public static boolean isValidId(String id) {
        return ENTRIES.containsKey(id);
    }

    /** プレイヤーが保持するユニーク能力IDを返す（未保持なら null）。 */
    public static String getAbility(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            return null;
        }
        WorldUniqueData data = WorldUniqueData.getOrCreate(overworld);
        for (String id : ABILITY_IDS) {
            if (data.hasId(player.getUuid(), id)) {
                return id;
            }
        }
        return null;
    }

    /** プレイヤーが指定IDの能力を持つか。 */
    public static boolean has(ServerPlayerEntity player, String id) {
        return id != null && id.equals(getAbility(player));
    }
}
