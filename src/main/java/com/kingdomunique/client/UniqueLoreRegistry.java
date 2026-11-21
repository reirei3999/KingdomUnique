package com.kingdomunique.client;

import com.kingdomunique.registry.AbilityDescriptions;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 能力・遺物・呪物の詳細説明を StatusScreen へ提供する（Gemini 提示を既存レジストリへ接続）。
 */
@Environment(EnvType.CLIENT)
public final class UniqueLoreRegistry {

    private UniqueLoreRegistry() {
    }

    public record UniqueInfo(
            String id,
            String name,
            String category,
            ItemStack icon,
            String triggerInfo,
            List<String> descriptionLines
    ) {
    }

    /** Gemini 提示の表記ゆれを既存IDへマップする（Map.of は10組までなので HashMap で構築）。 */
    private static final Map<String, String> ALIASES = new java.util.HashMap<>();

    static {
        ALIASES.put("overlord", "conqueror");
        ALIASES.put("phase_walk", "phase_through");
        ALIASES.put("slaughter_sword", "blade_of_slaughter");
        ALIASES.put("destructive_pickaxe", "pickaxe_of_destruction");
        ALIASES.put("architect_compass", "sages_blueprint");
        ALIASES.put("war_banner", "banner_of_victory");
        ALIASES.put("aegis_shield", "ward_stone");
        ALIASES.put("philosopher_stone", "golden_scale");
        ALIASES.put("royal_scepter", "chalice_of_abundance");
        ALIASES.put("wind_boots", "phoenix_feather");
        ALIASES.put("solitary_crown", "crown_of_solitude");
        ALIASES.put("blood_chalice", "ring_of_gluttony");
        ALIASES.put("cursed_shackles", "gravity_shackles");
        ALIASES.put("greed_idol", "golden_scale");
        ALIASES.put("abyssal_eye", "void_lantern");
        ALIASES.put("glass_cannon_orb", "glass_cannon");
        ALIASES.put("nether_brand", "immoral_contract");
    }

    /** IDから表示情報を取得する。未所持・未登録でも必ず何か返す。 */
    public static UniqueInfo getOrFallback(String rawId) {
        String id = rawId == null ? "" : ALIASES.getOrDefault(rawId, rawId);
        if (id.isEmpty()) {
            return new UniqueInfo("none", "未覚醒", "なし", new ItemStack(Items.BARRIER),
                    "-", List.of("まだユニーク能力を所持していません。", "ダイヤモンドの初取得などで覚醒します。"));
        }

        String name = ContentRegistry.nameOf(id);
        ContentRegistry.Kind kind = ContentRegistry.kindOf(id);
        String category = kind == ContentRegistry.Kind.RELIC ? "遺物"
                : kind == ContentRegistry.Kind.CURSE ? "呪物" : "能力";

        ItemStack icon = SelectionIcons.iconFor(id);
        String trigger = triggerInfo(id, kind);
        List<String> lines = Arrays.asList(AbilityDescriptions.details(id));
        return new UniqueInfo(id, name, category, icon, trigger, lines);
    }

    private static String triggerInfo(String id, ContentRegistry.Kind kind) {
        if (kind == ContentRegistry.Kind.ABILITY) {
            String hint = AbilityDescriptions.controlHint(id);
            return hint == null || hint.isEmpty() ? "常在効果" : hint;
        }
        return switch (id) {
            case "chalice_of_abundance", "banner_of_victory", "sages_blueprint", "golden_scale",
                 "ward_stone", "cradle_of_prosperity" -> "王国パッシブ（所持で常時発動）";
            case "phoenix_feather" -> "致命傷時に1日1回発動";
            case "artisans_hammer" -> "一定間隔で自動生産";
            case "blade_of_slaughter", "pickaxe_of_destruction" -> "専用アイテムとして付与";
            case "immoral_contract" -> "国民化のコスト0（死亡時に呪い）";
            case "glass_cannon", "crown_of_solitude", "berserker_mask", "chaos_dice",
                 "gravity_shackles", "ring_of_gluttony", "void_lantern" -> "常時パッシブ（代償あり）";
            default -> "所持しているだけで効果が発動";
        };
    }
}
