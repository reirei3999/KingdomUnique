package com.kingdomunique.client;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** カードに表示する象徴アイテムと効果説明（クライアント描画専用）。 */
public final class SelectionIcons {

    private SelectionIcons() {
    }

    public static ItemStack iconFor(String id) {
        ItemStack stack = switch (id) {
            case "divine_smith" -> new ItemStack(Items.DIAMOND_PICKAXE);
            case "trickery" -> new ItemStack(Items.ENCHANTED_BOOK);
            case "fertility" -> new ItemStack(Items.WHEAT);
            case "site_supervisor" -> new ItemStack(Items.OAK_PLANKS);
            case "conqueror" -> new ItemStack(Items.DIAMOND_SWORD);
            case "gale" -> new ItemStack(Items.FEATHER);
            case "indomitable" -> new ItemStack(Items.SHIELD);
            case "spatial_leap" -> new ItemStack(Items.ENDER_PEARL);
            case "phase_through" -> new ItemStack(Items.POWDER_SNOW_BUCKET);
            case "shadow_step" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
            case "absolute_dominion" -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case "gravity_control" -> new ItemStack(Items.ANVIL);
            case "chalice_of_abundance" -> new ItemStack(Items.GOLDEN_APPLE);
            case "banner_of_victory" -> new ItemStack(Items.RED_BANNER);
            case "sages_blueprint" -> new ItemStack(Items.MAP);
            case "golden_scale" -> new ItemStack(Items.GOLD_INGOT);
            case "ward_stone" -> new ItemStack(Items.OBSIDIAN);
            case "phoenix_feather" -> new ItemStack(Items.FEATHER);
            case "artisans_hammer" -> new ItemStack(Items.IRON_PICKAXE);
            case "blade_of_slaughter" -> new ItemStack(Items.DIAMOND_SWORD);
            case "cradle_of_prosperity" -> new ItemStack(Items.CRIMSON_FUNGUS);
            case "berserker_mask" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
            case "ring_of_gluttony" -> new ItemStack(Items.COOKED_BEEF);
            case "glass_cannon" -> new ItemStack(Items.TNT);
            case "crown_of_solitude" -> new ItemStack(Items.GOLDEN_HELMET);
            case "chaos_dice" -> new ItemStack(Items.EMERALD);
            case "gravity_shackles" -> new ItemStack(Items.CHAIN);
            case "immoral_contract" -> new ItemStack(Items.WRITTEN_BOOK);
            case "void_lantern" -> new ItemStack(Items.SOUL_LANTERN);
            default -> new ItemStack(Items.NETHERITE_PICKAXE);
        };
        return stack;
    }

    public static String[] effectsFor(String id) {
        return switch (id) {
            case "divine_smith" -> new String[]{"製作物にランダム強化", "Vキーで範囲採掘"};
            case "trickery" -> new String[]{"エンチャント上限を拡張"};
            case "fertility" -> new String[]{"作物成長を加速", "収穫ドロップ2倍"};
            case "site_supervisor" -> new String[]{"リーチ延長", "建築コスト/時間短縮"};
            case "conqueror" -> new String[]{"与ダメージ1.3倍", "ノックバック耐性"};
            case "gale" -> new String[]{"移動速度1.2倍", "落下ダメージ80%カット"};
            case "indomitable" -> new String[]{"8秒無傷で再生IV"};
            case "spatial_leap" -> new String[]{"最大8マス瞬間移動", "クールタイム5秒"};
            case "phase_through" -> new String[]{"3マス壁の通過", "クールタイム8秒"};
            case "shadow_step" -> new String[]{"敵の背後にワープ", "次攻撃1.5倍"};
            case "absolute_dominion" -> new String[]{"敵Mobを味方化", "_automatic追従AI"};
            case "gravity_control" -> new String[]{"敵を浮かせて拘束", "投擲で吹き飛ばす"};
            case "chalice_of_abundance" -> new String[]{"NPC回復2倍", "空腹無効"};
            case "banner_of_victory" -> new String[]{"兵士攻撃1.2倍", "英雄枠+1"};
            case "sages_blueprint" -> new String[]{"建築時間半減", "建築士効率2倍"};
            case "golden_scale" -> new String[]{"1日ごとに資材生成"};
            case "ward_stone" -> new String[]{"敵スポーン無効", "夜間被ダメ30%カット"};
            case "phoenix_feather" -> new String[]{"1日1回死亡時に復活"};
            case "artisans_hammer" -> new String[]{"装備・本を自動生産"};
            case "blade_of_slaughter" -> new String[]{"専用剣を付与", "撃破で攻撃力上昇"};
            case "cradle_of_prosperity" -> new String[]{"人口増加1.5倍"};
            case "berserker_mask" -> new String[]{"与ダメージ1.6倍", "自然回復不可"};
            case "ring_of_gluttony" -> new String[]{"撃破で一時強化", "空腹3倍・0で猛毒"};
            case "glass_cannon" -> new String[]{"攻撃/採掘2.5倍", "最大HP10固定"};
            case "crown_of_solitude" -> new String[]{"全能力1.8倍", "随伴不可"};
            case "chaos_dice" -> new String[]{"ダメージ0.7〜2.7倍"};
            case "gravity_shackles" -> new String[]{"被ダメ60%カット", "ジャンプ不可・速度0.8倍"};
            case "immoral_contract" -> new String[]{"国民コスト0", "国民死亡で呪いダメージ"};
            case "void_lantern" -> new String[]{"暗所で透明化・攻撃2倍", "明所で防御半減"};
            case "pickaxe_of_destruction" -> new String[]{"即破壊", "ドロップ消滅"};
            default -> new String[]{"世界で唯一の力"};
        };
    }

    /** 呪物のメリット行（_index 偶数）とデメリット行（奇数）を色分けするため、行番号を返す。 */
    public static int meritLineCount(String id) {
        return switch (id) {
            case "berserker_mask", "glass_cannon", "crown_of_solitude", "gravity_shackles",
                 "immoral_contract", "void_lantern", "pickaxe_of_destruction" -> 1;
            case "ring_of_gluttony" -> 1;
            default -> effectsFor(id).length;
        };
    }
}
