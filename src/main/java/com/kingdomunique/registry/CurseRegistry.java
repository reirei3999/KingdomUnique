package com.kingdomunique.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 呪物の定義。 */
public final class CurseRegistry {

    public static final String VAMPIRE_FANG = "vampire_fang";
    public static final String REAPER_HOURGLASS = "reaper_hourglass";
    public static final String ENVY_MIRROR = "envy_mirror";
    public static final String MAD_MOON_NECKLACE = "mad_moon_necklace";
    public static final String SEALING_CHAIN = "sealing_chain";

    private static final Map<String, String> NAMES;

    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("berserker_mask", "血塗られた狂戦士の仮面");
        map.put("ring_of_gluttony", "暴食の指輪");
        map.put("glass_cannon", "硝子の大砲");
        map.put("crown_of_solitude", "孤高の王冠");
        map.put("chaos_dice", "混沌のサイコロ");
        map.put("gravity_shackles", "重力の足枷");
        map.put("immoral_contract", "背徳の契約書");
        map.put("void_lantern", "虚無のランタン");
        map.put("pickaxe_of_destruction", "破壊衝動のツルハシ");
        map.put(VAMPIRE_FANG, "吸血鬼の牙");
        map.put(REAPER_HOURGLASS, "死神の砂時計");
        map.put(ENVY_MIRROR, "嫉妬の鏡");
        map.put(MAD_MOON_NECKLACE, "狂月の首飾り");
        map.put(SEALING_CHAIN, "封印の鎖");
        NAMES = Collections.unmodifiableMap(map);
    }

    public static final List<String> CURSE_IDS = List.copyOf(NAMES.keySet());

    public static final String BERSERKER = "berserker_mask";
    public static final String GLUTTONY = "ring_of_gluttony";
    public static final String GLASS_CANNON = "glass_cannon";
    public static final String SOLITUDE = "crown_of_solitude";
    public static final String CHAOS = "chaos_dice";
    public static final String SHACKLES = "gravity_shackles";
    public static final String CONTRACT = "immoral_contract";
    public static final String VOID_LANTERN = "void_lantern";
    public static final String DESTRUCTION = "pickaxe_of_destruction";

    private CurseRegistry() {
    }

    public static String getName(String id) {
        return NAMES.getOrDefault(id, id);
    }

    public static boolean isValid(String id) {
        return NAMES.containsKey(id);
    }
}
