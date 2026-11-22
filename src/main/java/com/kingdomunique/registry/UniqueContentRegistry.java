package com.kingdomunique.registry;

import com.kingdomunique.data.WorldUniqueData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界で一つだけ存在するユニーク能力。
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
        map.put("time_ruler", new UniqueEntry("time_ruler", "時の支配者", Category.SPECIAL));
        map.put("alchemy", new UniqueEntry("alchemy", "錬金術", Category.PRODUCTION));
        map.put("weather_control", new UniqueEntry("weather_control", "天候操作", Category.SPECIAL));
        ENTRIES = Collections.unmodifiableMap(map);
    }

    /** 能力のID（UNIQUE_IDS はステップ1からの互換名）。 */
    public static final List<String> ABILITY_IDS = List.copyOf(ENTRIES.keySet());
    /** 古代都市の宝箱を開けたときだけ候補に出る能力（ダイヤ取得・初回覚醒の抽選には出ない）。 */
    public static final List<String> ANCIENT_CITY_ABILITY_IDS = List.of("time_ruler", "alchemy", "weather_control");
    /** ダイヤ取得・初回覚醒で抽選される能力。 */
    public static final List<String> UNIQUE_IDS = ABILITY_IDS.stream()
            .filter(id -> !ANCIENT_CITY_ABILITY_IDS.contains(id)).toList();

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
    public static final String TIME_RULER = "time_ruler";
    public static final String ALCHEMY = "alchemy";
    public static final String WEATHER_CONTROL = "weather_control";

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
        List<String> abilities = getAbilities(player);
        return abilities.isEmpty() ? null : abilities.getFirst();
    }

    public static String getActiveAbility(ServerPlayerEntity player) {
        List<String> owned = getAbilities(player);
        for (String id : ANCIENT_CITY_ABILITY_IDS) {
            if (owned.contains(id)) {
                return id;
            }
        }
        for (String id : owned) {
            if (AbilityDescriptions.cooldownTicks(id) > 0) {
                return id;
            }
        }
        return null;
    }

    public static List<String> getAbilities(ServerPlayerEntity player) {
        if (player == null) {
            return List.of();
        }
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return List.of();
        }
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            return List.of();
        }
        WorldUniqueData data = WorldUniqueData.getOrCreate(overworld);
        List<String> abilities = new ArrayList<>();
        for (String id : ABILITY_IDS) {
            if (data.hasId(player.getUuid(), id)) {
                abilities.add(id);
            }
        }
        return abilities;
    }

    /** プレイヤーが指定IDの能力を持つか。 */
    public static boolean has(ServerPlayerEntity player, String id) {
        return id != null && getAbilities(player).contains(id);
    }
}
