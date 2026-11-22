package com.kingdomunique.client;

import com.kingdomunique.client.ui.KuTheme;
import com.kingdomunique.item.unique.UniqueArmors;
import com.kingdomunique.item.unique.UniqueWeaponManager;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.kingdomunique.registry.ContentRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Read-only catalogue. Item prose is checked against triggers/effects, not the design mockups. */
@Environment(EnvType.CLIENT)
final class UniqueJournalCatalog {
    enum Section {
        SKILL("スキル"), WEAPON("武器"), ARMOR("防具"), RELIC("遺物"), CURSE("呪物");
        final String label;
        Section(String label) { this.label = label; }
        int accent() { return this == RELIC ? KuTheme.CYAN : this == CURSE ? KuTheme.PURPLE : KuTheme.GOLD; }
    }

    record Entry(String id, String name, String category, ItemStack icon, Section section,
                 String controls, List<String> effects, String acquisition) {}

    private UniqueJournalCatalog() {}

    static List<Entry> entries(Section section) {
        if (section == Section.WEAPON) return weapons();
        if (section == Section.ARMOR) return armor();
        ContentRegistry.Kind kind = section == Section.RELIC ? ContentRegistry.Kind.RELIC
                : section == Section.CURSE ? ContentRegistry.Kind.CURSE : ContentRegistry.Kind.ABILITY;
        List<Entry> entries = new ArrayList<>();
        for (String id : ContentRegistry.idsOf(kind)) {
            var info = UniqueLoreRegistry.getOrFallback(id);
            String acquisition = switch (kind) {
                case ABILITY -> "ダイヤモンドの初取得で開く候補から1枚を選んで確定する。";
                case RELIC -> "国民15人の達成、または古代都市の宝箱の初開封で開く候補から1枚を選んで確定する。";
                case CURSE -> "ネザーの初到達で開く候補から1枚を選んで確定する。";
            };
            entries.add(new Entry(id, info.name(), info.category(), info.icon(), section,
                    info.triggerInfo(), info.descriptionLines(), acquisition));
        }
        return List.copyOf(entries);
    }

    // Acquisition: UniqueWeaponTriggers, TrishulaAcquisitionData, StormRaidMixin.
    // Effects: the item classes, UniqueWeaponManager and the corresponding damage mixins.
    private static List<Entry> weapons() {
        List<Entry> entries = new ArrayList<>();
        for (String id : UniqueWeapons.ALL_IDS) {
            entries.add(new Entry(id, weaponName(id), "ユニーク武器", UniqueWeaponManager.createStackForId(id),
                    Section.WEAPON, weaponControls(id), weaponEffects(id), weaponAcquisition(id)));
        }
        return List.copyOf(entries);
    }

    private static String weaponName(String id) {
        return switch (id) {
            case "excalibur" -> "聖剣 エクスカリバー";
            case "gae_bolg" -> "魔槍 ゲイボルグ";
            case "muramasa" -> "妖刀 ムラマサ";
            case "apollo_bow" -> "神弓 アポロンの太陽弓";
            case "mjolnir" -> "雷神銃 ミョルニル";
            case "chronos_dagger" -> "空間の短剣 クロノス・ダガー";
            case "kusanagi" -> "草薙の剣";
            case "poseidon_trident" -> "トライデント・オブ・ポセイドン";
            case "twilight_scythe" -> "黄昏の鎌（ハルパー）";
            case "trishula" -> "トリシューラ";
            default -> throw new IllegalArgumentException("Missing journal weapon: " + id);
        };
    }

    private static String weaponControls(String id) {
        return switch (id) {
            case "gae_bolg" -> "右クリックで投擲。クールタイム36秒。";
            case "apollo_bow" -> "左クリックで光の矢（クールタイム8秒）。右クリックで飛翔中の矢を起爆。";
            case "mjolnir" -> "右クリックで雷撃。クールタイム25秒。";
            case "kusanagi" -> "左クリックで風の斬撃。クールタイム10秒。";
            case "poseidon_trident" -> "右クリック長押しで投擲。投擲のクールタイム20秒。";
            case "trishula" -> "右クリックで投擲。クールタイム20秒。";
            default -> "メインハンドに持って攻撃。";
        };
    }

    private static List<String> weaponEffects(String id) {
        return switch (id) {
            case "excalibur" -> List.of("自国の領地内で、保持中の与ダメージが1.8倍。",
                    "周囲15ブロックの自国民に攻撃力上昇IIと再生Iを付与する。");
            case "gae_bolg" -> List.of("保持中の与ダメージが2.5倍。右クリックで高速の魔槍を投げる。",
                    "投擲後5秒以内に、この槍をメインハンドに持って何かを倒すと、クールタイムを18秒に短縮する。");
            case "muramasa" -> List.of("保持中の与ダメージの20%を回復するが、攻撃で空腹の消耗が増える。",
                    "この刀での撃破数に応じて攻撃力が増える（1体につき+0.35ダメージ）。");
            case "apollo_bow" -> List.of("光の矢はアンデッドへのダメージが2倍。",
                    "起爆すると周囲の生物（使用者以外）に15ダメージと5秒の燃焼・盲目を与える。");
            case "mjolnir" -> List.of("視線上64ブロック以内の対象に45の雷ダメージを与え、落雷を起こす。");
            case "chronos_dagger" -> List.of("素早く攻撃できる短剣。保持中の攻撃で20%の確率で敵を0.5秒停止する。");
            case "kusanagi" -> List.of("前方6ブロックの扇状範囲に6ダメージとノックバック。",
                    "雨天時は斬撃が9ダメージになり、ノックバックも強くなる。");
            case "poseidon_trident" -> List.of("水中で手に持つと移動しやすくなり、攻撃速度が25%上昇する。",
                    "投擲の着弾点に敵を引き寄せる渦潮を発生させる。");
            case "twilight_scythe" -> List.of("攻撃に敵の最大体力の5%を追加する。",
                    "地上の夜は、実際に減らした体力・吸収体力の10%を回復する。");
            case "trishula" -> List.of("プレイヤー以外への近接・投擲ダメージが2倍。",
                    "着弾時に小爆発。ブロックと投擲者は爆発のダメージを受けない。",
                    "着弾点の近くに安全な足場があればワープする。安全な場所がなければ移動しない。",
                    "ワープに成功すると5秒間、炎と落下のダメージを無効化する。");
            default -> throw new IllegalArgumentException("Missing journal weapon: " + id);
        };
    }

    private static String weaponAcquisition(String id) {
        return switch (id) {
            case "excalibur" -> "自国の人口を25人以上にする。";
            case "gae_bolg" -> "地上の満月の夜、空が見える場所でスニークし、上空を3秒間見上げ続ける。";
            case "muramasa" -> "種類を問わず、累計100体を倒す。";
            case "apollo_bow" -> "地上の昼、高さY=300以上で上空を見上げ、弓を放つ。";
            case "mjolnir" -> "ウィザー、またはエンダードラゴンを倒す。";
            case "chronos_dagger" -> "金床での強化を+4以上に到達させる。";
            case "kusanagi" -> "雷雨中に始まった襲撃を、雷雨を途切れさせず、村人を死なせずに勝利する。対象となる英雄のうち1人に授与される。";
            case "poseidon_trident" -> "同じ海底神殿に属する異なるエルダーガーディアンを3体倒す。";
            case "twilight_scythe" -> "地上の新月の夜にエンダーマンを累計20体倒す。";
            case "trishula" -> "ウィザー撃破後、ネザー要塞内の異なる座標にあるブレイズスポナーを3個破壊する。ウィザー撃破前の破壊は数えない。";
            default -> throw new IllegalArgumentException("Missing journal weapon: " + id);
        };
    }

    // Acquisition: UniqueArmorTriggers + ArmorEnchantingMixin (level-30 enchant hook).
    // Effects: UniqueArmorEffects, ArmorAttributesMixin, CooledLavaBlock.
    private static List<Entry> armor() {
        return List.of(
                armor("hades_helmet", "ハデスの隠れ兜", UniqueArmors.HADES_HELMET, "頭に装備。地面で静止し、3秒間スニーク。",
                        List.of("透明化し、周囲の敵から狙われにくくなる。攻撃・被ダメージなどで解除される。", "解除後のクールタイムは30秒。"),
                        "ウォーデンを倒す。"),
                armor("mimir_helmet", "ミーミルの叡智の兜", UniqueArmors.MIMIR_HELMET, "頭に装備して使用。",
                        List.of("エンチャントと金床で実際に消費するレベルを30%軽減する。", "四捨五入し、正の消費は最低1レベル。要求レベルそのものは変わらない。"),
                        "有効な本棚15個のある同じエンチャントテーブルで、要求レベル30以上のエンチャントを10回行う。"),
                armor("dragonscale_wingplate", "竜鱗の翼鎧", UniqueArmors.DRAGONSCALE_WINGPLATE, "胴に装備。空中でジャンプキーを押すと滑空。",
                        List.of("エリトラのように滑空できる。", "炎・爆発によるダメージを40%軽減する。"),
                        "地上の建築上限（通常Y=320）以上から連続して落下し、最下層付近（通常Y=-62以下）の地面か水に生還する。飛行・滑空・乗車などで中断すると無効。"),
                armor("nemean_lion_hide", "ネメアの獅子皮", UniqueArmors.NEMEAN_LION_HIDE, "胴に装備。素手で攻撃。",
                        List.of("近接攻撃の被ダメージを35%軽減する。", "メインハンドが空のとき、攻撃力が4ダメージ上昇する。"),
                        "メインハンドを空にし、直接の近接攻撃でラヴェジャーを倒す。"),
                armor("jotun_leggings", "ヨトゥンの巨人脚甲", UniqueArmors.JOTUN_LEGGINGS, "脚に装備。スニークで踏ん張る。",
                        List.of("スニーク中は被ダメージを40%軽減する。", "スニーク中は通常・爆発のノックバックに耐える。"),
                        "トライアルチャンバー内の異なる不吉な宝物庫を5か所開ける。"),
                armor("hermes_talaria", "ヘルメスのタラリア", UniqueArmors.HERMES_TALARIA, "足に装備。空中でもう一度ジャンプキー。",
                        List.of("空中ジャンプが1回でき、着地すると回数が戻る。クールタイム3秒。", "水中・溶岩内・滑空中・乗車中や、ジャンプを封じられている間は使えない。"),
                        "地上の同じ1夜の間にファントムを10体倒す。"),
                armor("salamander_boots", "サラマンダーの靴", UniqueArmors.SALAMANDER_BOOTS, "足に装備して歩行。",
                        List.of("装備中は火を消し、足元の半径2ブロック以内の溶岩源を一時的な足場にする。", "足場は3〜5秒で溶岩に戻る。乗車・滑空・飛行中は足場を作らない。"),
                        "溶岩上のストライダーに乗り、水平距離で累計333ブロック移動する。"));
    }

    private static Entry armor(String id, String name, Item item, String controls, List<String> effects, String acquisition) {
        return new Entry(id, name, "ユニーク防具", new ItemStack(item), Section.ARMOR, controls, effects, acquisition);
    }
}
