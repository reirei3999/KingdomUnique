package com.kingdomunique.client;

import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.registry.RelicRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** カードに表示する象徴アイテムと効果説明（クライアント描画専用）。 */
public final class SelectionIcons {

    private SelectionIcons() {
    }

    public static ItemStack iconFor(String id) {
        ItemStack stack = switch (id) {
            case "divine_smith" -> new ItemStack(Items.DIAMOND_PICKAXE);
            case "trickery" -> new ItemStack(Items.ENDER_PEARL);
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
            case "time_ruler" -> new ItemStack(Items.CLOCK);
            case "alchemy" -> new ItemStack(Items.BREWING_STAND);
            case "weather_control" -> new ItemStack(Items.LIGHTNING_ROD);
            case "chalice_of_abundance" -> new ItemStack(Items.GOLDEN_APPLE);
            case "banner_of_victory" -> new ItemStack(Items.RED_BANNER);
            case "sages_blueprint" -> new ItemStack(Items.MAP);
            case "golden_scale" -> new ItemStack(Items.GOLD_INGOT);
            case "ward_stone" -> new ItemStack(Items.OBSIDIAN);
            case "phoenix_feather" -> new ItemStack(Items.FEATHER);
            case "artisans_hammer" -> new ItemStack(Items.IRON_PICKAXE);
            case "blade_of_slaughter" -> new ItemStack(Items.DIAMOND_SWORD);
            case "cradle_of_prosperity" -> new ItemStack(Items.CRIMSON_FUNGUS);
            case RelicRegistry.CRYSTAL -> new ItemStack(Items.AMETHYST_SHARD);
            case RelicRegistry.COMPASS -> new ItemStack(Items.COMPASS);
            case RelicRegistry.GUARDIAN_EGG -> new ItemStack(Items.EGG);
            case "berserker_mask" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
            case "ring_of_gluttony" -> new ItemStack(Items.COOKED_BEEF);
            case "glass_cannon" -> new ItemStack(Items.TNT);
            case "crown_of_solitude" -> new ItemStack(Items.GOLDEN_HELMET);
            case "chaos_dice" -> new ItemStack(Items.EMERALD);
            case "gravity_shackles" -> new ItemStack(Items.CHAIN);
            case "immoral_contract" -> new ItemStack(Items.WRITTEN_BOOK);
            case "void_lantern" -> new ItemStack(Items.SOUL_LANTERN);
            case CurseRegistry.VAMPIRE_FANG -> new ItemStack(Items.GHAST_TEAR);
            case CurseRegistry.REAPER_HOURGLASS -> new ItemStack(Items.CLOCK);
            case CurseRegistry.ENVY_MIRROR -> new ItemStack(Items.GLASS_PANE);
            case CurseRegistry.MAD_MOON_NECKLACE -> new ItemStack(Items.AMETHYST_SHARD);
            case CurseRegistry.SEALING_CHAIN -> new ItemStack(Items.CHAIN);
            default -> new ItemStack(Items.NETHERITE_PICKAXE);
        };
        return stack;
    }

    public static String[] effectsFor(String id) {
        return switch (id) {
            case "divine_smith" -> new String[]{"確率で強化数値アップ", "Vキーで範囲採掘（最大3x3）"};
            case "trickery" -> new String[]{"視線20マスの対象と入れ替わり", "盲目+鈍足付与＆分身でヘイト回避", "_CT 12秒"};
            case "fertility" -> new String[]{"作物成長を加速", "収穫ドロップ2倍"};
            case "site_supervisor" -> new String[]{"リーチ延長", "建築コスト/時間短縮"};
            case "conqueror" -> new String[]{"与ダメージ1.3倍", "ノックバック耐性"};
            case "gale" -> new String[]{"移動速度1.2倍", "落下ダメージ80%カット"};
            case "indomitable" -> new String[]{"10秒無傷で再生III", "常時耐性II"};
            case "spatial_leap" -> new String[]{"最大8マス瞬間移動", "クールタイム5秒"};
            case "phase_through" -> new String[]{"3マス壁の通過", "クールタイム8秒"};
            case "shadow_step" -> new String[]{"敵の背後にワープ", "次攻撃1.5倍"};
            case "absolute_dominion" -> new String[]{"敵Mobを味方化", "_automatic追従AI"};
            case "gravity_control" -> new String[]{"敵を浮かせて拘束", "投擲で吹き飛ばす"};
            case "time_ruler" -> new String[]{"周囲8マスの敵を3秒停止", "クールタイム40秒"};
            case "alchemy" -> new String[]{"手持ちブロックを1段上の素材へ", "1日10回まで"};
            case "weather_control" -> new String[]{"晴れ・雨・雷雨を切替", "CT ゲーム内1日"};
            case "chalice_of_abundance" -> new String[]{"NPC回復2倍", "空腹ステータス効果解除"};
            case "banner_of_victory" -> new String[]{"兵士攻撃1.2倍", "英雄枠+1"};
            case "sages_blueprint" -> new String[]{"建築時間半減", "建築士効率2倍"};
            case "golden_scale" -> new String[]{"1日ごとに資材生成"};
            case "ward_stone" -> new String[]{"敵スポーン無効", "夜間被ダメ30%カット"};
            case "phoenix_feather" -> new String[]{"1日1回死亡時に復活"};
            case "artisans_hammer" -> new String[]{"30秒ごと12%でエンチャント本を生成"};
            case "blade_of_slaughter" -> new String[]{"専用剣を付与", "中立・敵対Mob撃破で攻撃力上昇"};
            case "cradle_of_prosperity" -> new String[]{"人口増加1.5倍"};
            case RelicRegistry.CRYSTAL -> new String[]{"領内への侵入を通知・発光表示"};
            case RelicRegistry.COMPASS -> new String[]{"1日1回 /ku recall で帰還"};
            case RelicRegistry.GUARDIAN_EGG -> new String[]{"守護獣1体を召喚", "倒されると翌日復活"};
            case "berserker_mask" -> new String[]{"与ダメージ1.6倍", "自然回復不可"};
            case "ring_of_gluttony" -> new String[]{"撃破で一時強化", "空腹3倍・0で猛毒"};
            case "glass_cannon" -> new String[]{"攻撃2.5倍・採掘5倍", "最大HP10固定"};
            case "crown_of_solitude" -> new String[]{"攻防1.5倍・採掘3倍・移動1.2倍", "随伴不可"};
            case "chaos_dice" -> new String[]{"ダメージ0.2〜2.7倍"};
            case "gravity_shackles" -> new String[]{"被ダメ60%カット", "ジャンプ不可・速度0.8倍"};
            case "immoral_contract" -> new String[]{"国民コスト0", "国民死亡で呪いダメージ"};
            case "void_lantern" -> new String[]{"暗所で透明化・攻撃2倍", "明所で防御半減"};
            case "pickaxe_of_destruction" -> new String[]{"即破壊", "ドロップ消滅"};
            case CurseRegistry.VAMPIRE_FANG -> new String[]{"与ダメージの25%吸血・夜の移動1.3倍", "日光で燃焼（ヘルメットで防御）"};
            case CurseRegistry.REAPER_HOURGLASS -> new String[]{"低体力ほど攻撃上昇（最大2.5倍）", "最大体力-30%・回復量半減"};
            case CurseRegistry.ENVY_MIRROR -> new String[]{"被ダメージの30%を反射", "味方・自国民からの回復や強化が無効"};
            case CurseRegistry.MAD_MOON_NECKLACE -> new String[]{"夜は攻撃1.5倍・移動1.3倍", "昼は攻撃0.7倍・睡眠不可"};
            case CurseRegistry.SEALING_CHAIN -> new String[]{"被ダメージ半減・ノックバック無効", "ユニーク能力が使えない"};
            default -> new String[]{"世界で唯一の力"};
        };
    }

    /** 呪物のメリット行（_index 偶数）とデメリット行（奇数）を色分けするため、行番号を返す。 */
    public static int meritLineCount(String id) {
        return switch (id) {
            case "berserker_mask", "glass_cannon", "crown_of_solitude", "gravity_shackles",
                 "immoral_contract", "void_lantern", "pickaxe_of_destruction",
                 CurseRegistry.VAMPIRE_FANG, CurseRegistry.REAPER_HOURGLASS, CurseRegistry.ENVY_MIRROR,
                 CurseRegistry.MAD_MOON_NECKLACE, CurseRegistry.SEALING_CHAIN -> 1;
            case "ring_of_gluttony" -> 1;
            default -> effectsFor(id).length;
        };
    }
}
