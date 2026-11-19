package com.kingdomunique.registry;

/** ユニーク能力の詳細説明・操作方法・クールタイム定義（サーバー/クライアント共通）。 */
public final class AbilityDescriptions {

    private AbilityDescriptions() {
    }

    /** アクティブ能力のクールタイム（ティック）。0 はパッシブ。 */
    public static int cooldownTicks(String id) {
        if (id == null) {
            return 0;
        }
        return switch (id) {
            case UniqueContentRegistry.SPATIAL_LEAP -> 100;   // 5秒
            case UniqueContentRegistry.PHASE_THROUGH -> 160;  // 8秒
            case UniqueContentRegistry.SHADOW_STEP -> 200;    // 10秒
            case UniqueContentRegistry.GRAVITY_CONTROL -> 300; // 15秒
            case UniqueContentRegistry.TRICKERY -> 240;       // 12秒
            default -> 0;
        };
    }

    /** 操作キーのヒント。 */
    public static String controlHint(String id) {
        int ct = cooldownTicks(id);
        if (ct > 0) {
            return "Rキー：発動（CT " + (ct / 20) + "秒）";
        }
        if (UniqueContentRegistry.DIVINE_SMITH.equals(id)) {
            return "Vキー：採掘範囲切替 / 金床で無限強化";
        }
        return "常在効果（操作キーなし）";
    }

    /** 詳細説明行（確認画面・チャット通知用）。 */
    public static String[] details(String id) {
        if (id == null) {
            return new String[]{"世界で唯一の能力。"};
        }
        return switch (id) {
            case UniqueContentRegistry.DIVINE_SMITH -> new String[]{
                    "・金床で同じアイテム同士を合成すると無限強化（+1、稀に+2/+3）",
                    "・完成品は攻撃力・攻撃速度・採掘速度・耐久が 1.1^n 倍",
                    "・Vキーで採掘範囲を 1x1 / 3x3 / 5x5 に切り替え"
            };
            case UniqueContentRegistry.TRICKERY -> new String[]{
                    "・Rキー：視線20マス以内の対象と位置・向きを入れ替える",
                    "・相手に2秒の盲目と鈍足Iを付与、両地点にポータル粒子",
                    "・元の位置に5秒間、敵のヘイトを引く分身を召喚",
                    "・対象がいない場合はクールタイムを消費しない（CT 12秒）"
            };
            case UniqueContentRegistry.FERTILITY -> new String[]{
                    "・半径16マスの作物成長を加速",
                    "・成熟作物の収穫ドロップが2倍"
            };
            case UniqueContentRegistry.SITE_SUPERVISOR -> new String[]{
                    "・ブロック／エンティティのリーチが+3",
                    "・建築コスト30%OFF・時間30%短縮・採掘速度向上"
            };
            case UniqueContentRegistry.CONQUEROR -> new String[]{
                    "・与ダメージが1.3倍（近接・遠距離ともに有効）",
                    "・ノックバック耐性を獲得"
            };
            case UniqueContentRegistry.GALE -> new String[]{
                    "・移動速度1.2倍",
                    "・落下ダメージ80%カット"
            };
            case UniqueContentRegistry.INDOMITABLE -> new String[]{
                    "・8秒間ダメージを受けなければ再生IVが継続付与"
            };
            case UniqueContentRegistry.SPATIAL_LEAP -> new String[]{
                    "・Rキー：視線方向最大8マスの安全な地点へ瞬間移動",
                    "・CT 5秒"
            };
            case UniqueContentRegistry.PHASE_THROUGH -> new String[]{
                    "・Rキー：厚さ3マス以内の壁を抜けて反対側へ移動",
                    "・CT 8秒"
            };
            case UniqueContentRegistry.SHADOW_STEP -> new String[]{
                    "・Rキー：視線15マスの敵の背後にワープ",
                    "・次の攻撃が1.5倍、CT 10秒"
            };
            case UniqueContentRegistry.ABSOLUTE_DOMINION -> new String[]{
                    "・敵対Mob撃破時に25%で味方化して蘇生",
                    "・味方は自動で追従・戦闘する"
            };
            case UniqueContentRegistry.GRAVITY_CONTROL -> new String[]{
                    "・Rキー：視線先の敵を浮かせて拘束、再押下で解放",
                    "・落下ダメージ50%カット、CT 15秒"
            };
            default -> new String[]{"世界で唯一の能力。"};
        };
    }
}
