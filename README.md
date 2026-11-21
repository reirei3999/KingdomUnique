# KingdomUnique

「建国とユニーク能力」をテーマにした Minecraft 1.21.1 / Fabric Mod（ステップ1〜3実装）。

## ディレクトリ構成

```
KingdomUnique/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── .gitignore
├── README.md
└── src/main/
    ├── java/com/kingdomunique/
    │   ├── KingdomUniqueMod.java          # サーバー側エントリポイント
    │   ├── KingdomUniqueClientMod.java    # クライアント側エントリポイント
    │   ├── ability/                       # 12能力の効果・状態・イベント（ステップ3）
    │   │   ├── AbilityActions.java        # Rキー発動（跳躍/透過/影渡り/重力）
    │   │   ├── AbilityEventHandler.java   # イベント・パケット登録
    │   │   ├── AbilityPassives.java       # 常時効果（属性/不屈/豊穣/重力）
    │   │   ├── AbilityState.java
    │   │   ├── AlliedFollowGoal.java      # 味方Mobの追従AI
    │   │   ├── AlliedMobManager.java      # 絶対支配の蘇生・管理
    │   │   ├── PlayerAbilityState.java
    │   │   └── SiteSupervisorUtil.java    # 建築時間/資材コスト倍率
    │   │       # 奇術は AbilityActions#trickery に実装（位置シャッフル＋デコイ）
    │   ├── client/
    │   │   └── KeyBindings.java           # R / V キーバインド
    │   ├── network/
    │   │   └── AbilityKeyPayload.java     # CustomPayload (C2S)
    │   ├── command/
    │   │   └── ShowItemCommand.java       # /i コマンド（アイテム披露）
    │   ├── component/
    │   │   └── ModDataComponents.java     # enhance_level カスタム DataComponent
    │   ├── data/
    │   │   └── WorldUniqueData.java       # PersistentState による排他管理
    │   ├── mixin/
    │   │   ├── AnvilScreenHandlerMixin.java  # 金床の無限強化
    │   │   ├── AreaMiningMixin.java          # 神匠の範囲採掘
    │   │   ├── CraftBonusMixin.java          # 神匠のクラフトボーナス
    │   │   ├── CropDropMixin.java            # 豊穣のドロップ2倍
    │   │   └── LivingEntityMixin.java        # 覇王/影渡り/味方保護
    │   ├── registry/
    │   │   └── UniqueContentRegistry.java # ユニークID定義（12 + 神匠）
    │   ├── util/
    │   │   └── EnhancementUtil.java       # 1.1^N 強化計算
    │   └── world/
    │       ├── WorldInitializer.java      # ボーダー + 村配置の初期化
    │       └── VillageBuilder.java        # 村のブロック生成と村人スポーン
    └── resources/
        ├── fabric.mod.json
        ├── kingdomunique.mixins.json
        └── assets/kingdomunique/lang/            # キーバインド表示名
        └── data/minecraft/worldgen/structure_set/
            └── villages.json             # バニラ村の structure set を空にして無限生成を抑制
```

## ビルド

Java 21 が必要です。

```powershell
gradlew build
```

生成された `build/libs/kingdomunique-0.1.0.jar` を `mods` フォルダに配置してください。

ステップ6時点で Gradle 8.8 による `./gradlew build` を実行し、`BUILD SUCCESSFUL` を確認済みです（`kingdomunique-0.1.0.jar` が生成されます）。

## ステップ1の内容

1. **エントリポイント**: `KingdomUniqueMod` / `KingdomUniqueClientMod`
2. **ワールド制限**
   - オーバーワールド初回読み込み時にボーダーを中心 `(0,0)`・`5000x5000` に設定。
   - バニラ村の `structure_set`（`minecraft:villages`）をデータパックで上書きして `structures` を空にし、無限生成を抑制。
   - 12個のグリッドから10箇所を選び、互いに約800ブロック以上離れた座標に村を生成。
3. **排他管理データ**: `WorldUniqueData extends PersistentState`
   - `getAvailableIds(Collection<String>)` / `claimId(UUID, String)` を提供。
4. **初回覚醒**: `ServerPlayConnectionEvents.JOIN` で未保持プレイヤーに未取得のユニークを1つ付与し、チャットで通知。

## ステップ2の内容

1. **無限強化合成**
   - 金床の左右に「同じアイテム・同じ強化段階」を入れると `+N` が上昇（上限なし）。
   - 対象: 剣・斧・ツルハシ・シャベル・クワ（ToolItem）、弓・クロスボウ（RangedWeaponItem）、トライデント、メイス、各種防具。
   - 性能は段階 `N` に対して `1.1^N` 倍。`AttributeModifiersComponent` と `MAX_DAMAGE` を動的に再計算。
   - 表示名は `ダイヤモンドの剣 +3` のように末尾へ自動付与。
   - ユニーク能力「神匠」（`divine_smith`）所持時は 15% で +3、25% で +2、それ以外は通常 +1。
   - 強化段階はカスタム DataComponent `kingdomunique:enhance_level`（`Codec.INT` + `ByteBufCodecs.VAR_INT`）に永続保存。
2. **`/i` コマンド**
   - メインハンドのアイテム名・強化段階・倍率・耐久・属性補正・エンチャントをホバーテキスト付きで全プレイヤーへ共有。
   - 空手の場合は本人にエラーメッセージを表示。
3. **Mixin**: `AnvilScreenHandlerMixin` が `updateResult()` を条件付きでキャンセルし、結果スロットを差し替え。

## ステップ3の内容

- **キーバインド / パケット**: R（能力発動）/ V（モード切替）を `KeyBindingHelper` で登録し、`ClientPlayNetworking.send` → `ServerPlayNetworking.registerGlobalReceiver` で `CustomPayload`（`PayloadTypeRegistry.playC2S()` + `CustomPayload.codecOf`）を送信。
- **12能力すべてを実装**:
  - 生産: 神匠（クラフト時ボーナス＋3段階の範囲採掘）、奇術（エンチャ上限1.5〜2倍）、豊穣（半径16の成長加速＋ドロップ2倍）、現場監督（リーチ/採掘速度＋建築倍率メソッドを用意）
  - 戦闘: 覇王（ダメージ1.3倍＋ノックバック耐性）、疾風（移動1.2倍＋落下80%カット）、不屈（8秒無傷で再生IV）
  - 特殊: 空間跳躍（8マス/5秒）、物質透過（3マス壁/8秒）、影渡り（15マス背後＋次攻撃1.5倍/10秒）、絶対支配（25%で敵を味方化し追従AI）、重力操作（5秒拘束→投擲、落下ダメージ半減）
- 能力の判定はすべて `WorldUniqueData` の保持IDを参照し、該当プレイヤーにのみ効果が適用される。

## ステップ4の内容

- **排他データの拡張**: `WorldUniqueData` を「UUID -> 取得ID集合」に変更し、1プレイヤーが能力＋遺物＋呪物を保持できるように。選択確定時に即 `claimId()` で世界ロック。
- **選択GUI**: `OpenSelectionPayload`(S2C) で選択肢を渡し、`SelectionScreen`（`Screen` + ボタン）を表示。クリック時に `SelectionChoicePayload`(C2S) を送り、サーバーが排他取得。未選択分は再ログイン時に再表示。`/ku select` と各カテゴリの手動選択コマンドはOP権限レベル2以上のみ使用可能。
- **トリガー**: ダイヤモンド初取得（能力2択）、`onCitizenCountReached(player, 15)`（遺物3択）、ネザー初到達（呪物5択）。
- **遺物9種 / 呪物9種**: 属性補正・Tick処理・死亡イベント・専用アイテム（殺戮の宝剣／破壊衝動のツルハシ）として実装。
- **倍率メソッド**: `RelicEffects.getBuildTimeMultiplier()` / `getPopulationGrowthMultiplier()` などを用意し、将来の建国システムから利用可能。

## ステップ5の内容

- **王国データ**: `KingdomData extends PersistentState` に王ごとの中心座標・人口・上限（初期10、住宅ごとに+5、最大40）・英雄召喚回数を保存。
- **初期村**: ステップ1の10村ロード時に各村の村人を6人へ調整。15人到達で `onCitizenCountReached` を自動呼び出し。
- **国民化**: 「王国の証」を村人に使用、または食料/エメラルド消費で国民化。手に持った道具から農民／建築士／鍛冶屋／兵士を割当て、強化済み装備をそのまま兵士へ装備。繁栄の揺りかごで成長1.5倍、背徳の契約書で国民死亡時に王へ呪いダメージ。
- **英雄NPC**: `HeroEntity`（`PathAwareEntity` 拡張）と4クラス（刺客／騎士／弓手／鍛冶師）。角笛で1王国1回（軍旗で2回）召喚、雷鳴と全体公告。右クリックで随伴／防衛／待機を切替（孤高の王冠で随伴不可）。
- **憑依**: Gキー／憑依のオーブで刺客英雄へ視点と操作を切替。スニーク＋ジャンプで大ジャンプ、刺客は落下ダメージ無効。

## ステップ6以降の予定

- 設計図建築と村人交易の実装（`SiteSupervisorUtil` / `RelicEffects` の倍率を利用）
- クライアント側 HUD と能力アイコン表示
