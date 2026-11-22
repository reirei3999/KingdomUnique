package com.kingdomunique.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 遺物の定義。 */
public final class RelicRegistry {

    private static final Map<String, String> NAMES;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("chalice_of_abundance", "豊穣の聖杯");
        map.put("banner_of_victory", "戦勝の軍旗");
        map.put("sages_blueprint", "賢者の設計盤");
        map.put("golden_scale", "黄金の天秤");
        map.put("ward_stone", "守護の結界石");
        map.put("phoenix_feather", "不死鳥の羽根");
        map.put("artisans_hammer", "職人の神槌");
        map.put("blade_of_slaughter", "殺戮の宝剣");
        map.put("cradle_of_prosperity", "繁栄の揺りかご");
        map.put("clairvoyant_crystal", "千里眼の水晶");
        map.put("travelers_compass", "旅人の羅針盤");
        map.put("guardian_egg", "守護獣の卵");
        NAMES = Collections.unmodifiableMap(map);
    }

    public static final List<String> RELIC_IDS = List.copyOf(NAMES.keySet());

    public static final String CHALICE = "chalice_of_abundance";
    public static final String BANNER = "banner_of_victory";
    public static final String BLUEPRINT = "sages_blueprint";
    public static final String GOLDEN_SCALE = "golden_scale";
    public static final String WARD_STONE = "ward_stone";
    public static final String PHOENIX = "phoenix_feather";
    public static final String HAMMER = "artisans_hammer";
    public static final String BLADE = "blade_of_slaughter";
    public static final String CRADLE = "cradle_of_prosperity";
    public static final String CRYSTAL = "clairvoyant_crystal";
    public static final String COMPASS = "travelers_compass";
    public static final String GUARDIAN_EGG = "guardian_egg";

    private RelicRegistry() {
    }

    public static String getName(String id) {
        return NAMES.getOrDefault(id, id);
    }

    public static boolean isValid(String id) {
        return NAMES.containsKey(id);
    }
}
