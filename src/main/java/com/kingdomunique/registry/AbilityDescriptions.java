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

    /**
     * 残りティックを0.5秒刻みの文字列にする（小数部は 0.0 か 0.5 のみ）。
     */
    public static String formatSeconds(int ticks) {
        double seconds = Math.max(0.0D, ticks / 20.0D);
        double rounded = Math.round(seconds * 2.0D) / 2.0D;
        return String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }

    /** 操作キーのヒント。 */
    public static String controlHint(String id) {
        int ct = cooldownTicks(id);
        if (ct > 0) {
            return "Rキー：発動（CT " + (ct / 20) + "秒）";
        }
        if (UniqueContentRegistry.DIVINE_SMITH.equals(id)) {
            return "Vキー：採掘範囲切替（最大3x3）/ 確率で強化数値アップ";
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
                    "・確率で強化数値アップ：金床で同じアイテム・同じ段階を合成すると、60%で+1・25%で+2・15%で+3",
                    "・強化品の基本性能・耐久は 1.1^n 倍、ツール採掘速度は 2×1.1^n 倍",
                    "・製作時の採掘速度ボーナスが付いたツールは、従来のボーナス込み速度からさらに2倍",
                    "・Vキーで採掘範囲を 1x1 / 3x3（最大）に切り替え"
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
                    "・建築コスト30%OFF・時間30%短縮・採掘速度3倍"
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
            // ---- 遺物（レリック） ----
            case "chalice_of_abundance" -> new String[]{
                    "・王国内のNPC回復量が2倍",
                    "・空腹のステータス効果を解除（満腹度は通常通り減少）"
            };
            case "banner_of_victory" -> new String[]{
                    "・自国兵士NPCの攻撃力1.2倍",
                    "・英雄の召喚上限が+1"
            };
            case "sages_blueprint" -> new String[]{
                    "・建築時間が50%短縮",
                    "・建築士NPCの作業効率が2倍"
            };
            case "golden_scale" -> new String[]{
                    "・1日ごとに周囲256ブロック以内の自身の登録国民数に応じたエメラルドと鉄を生成"
            };
            case "ward_stone" -> new String[]{
                    "・自身の半径128ブロック以内の敵対Mobの自然スポーンを無効",
                    "・既存のMob・ボス・召喚やスポナーには影響しない",
                    "・夜間の被ダメージ30%カット"
            };
            case "phoenix_feather" -> new String[]{
                    "・1日1回、致命傷をキャンセルして全回復",
                    "・空腹も全回復する"
            };
            case "artisans_hammer" -> new String[]{
                    "・30秒ごとに12%の確率で、ランダムな有効レベルのエンチャント本を生成"
            };
            case "blade_of_slaughter" -> new String[]{
                    "・専用の殺戮の宝剣を付与",
                    "・中立・敵対Mobの撃破ごとに攻撃力+0.05が永久に蓄積（動物・村人などの友好Mobは対象外）"
            };
            case "cradle_of_prosperity" -> new String[]{
                    "・王国の人口増加速度が1.5倍"
            };
            // ---- 呪物（カース） ----
            case "berserker_mask" -> new String[]{
                    "・与ダメージ1.6倍",
                    "・自然回復が不能になる"
            };
            case "ring_of_gluttony" -> new String[]{
                    "・撃破で一時的に攻撃力が上昇",
                    "・空腹の減少3倍、空腹0で猛毒"
            };
            case "glass_cannon" -> new String[]{
                    "・攻撃力2.5倍・採掘速度5倍",
                    "・最大HPが10に固定される"
            };
            case "crown_of_solitude" -> new String[]{
                    "・攻撃力・防御力1.5倍、採掘速度3倍、移動速度1.2倍",
                    "・味方や随伴者を連れられない"
            };
            case "chaos_dice" -> new String[]{
                    "・攻撃ごとにダメージ0.2〜2.7倍（期待値1.45倍）"
            };
            case "gravity_shackles" -> new String[]{
                    "・被ダメージ60%カット",
                    "・ジャンプ不可・移動速度0.8倍・状態異常を無効化"
            };
            case "immoral_contract" -> new String[]{
                    "・国民化のコストが0になる",
                    "・自身の国民が原因を問わず死亡すると呪いダメージ（ログイン中・別次元も対象）",
                    "・国民以外の村人を自ら倒した場合も呪いダメージ"
            };
            case "void_lantern" -> new String[]{
                    "・暗所では透明化＋攻撃力2倍",
                    "・明所では防御力が半減する"
            };
            case "pickaxe_of_destruction" -> new String[]{
                    "・採掘速度402倍、専用ツルハシでどんなブロックも一瞬で破壊",
                    "・破壊したブロックのドロップは消滅"
            };
            default -> new String[]{"世界で唯一の力。所持しているだけで効果が発動します。"};
        };
    }
}
