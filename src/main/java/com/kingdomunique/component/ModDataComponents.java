package com.kingdomunique.component;

import com.kingdomunique.KingdomUniqueMod;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * KingdomUnique のカスタム Data Component 登録。
 *
 * 1.21.1 ではカスタムコンポーネントの型は {@link ComponentType} であり、
 * {@link net.minecraft.component.DataComponentTypes} はバニラ標準コンポーネントの定義クラス。
 * ここで登録した {@link #ENHANCE_LEVEL} はアイテムの NBT / ネットワーク同期の両方に対応し、
 * 強化段階をアイテム自体に永続保存する。
 */
public final class ModDataComponents {

    /** 強化段階（0 = 未強化、1以上 = +N）。 */
    public static final ComponentType<Integer> ENHANCE_LEVEL = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "enhance_level"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.VAR_INT)
                    .build()
    );

    /** 殺戮の宝剣が撃破ごとに積み上げる攻撃力ボーナス。 */
    public static final ComponentType<Float> SLAUGHTER_BONUS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "slaughter_bonus"),
            ComponentType.<Float>builder()
                    .codec(Codec.FLOAT)
                    .packetCodec(PacketCodecs.FLOAT)
                    .build()
    );

    /** 世界に一つだけのユニーク武器ID（無限強化対象外の判定にも使用）。 */
    public static final ComponentType<String> UNIQUE_WEAPON_ID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "unique_weapon_id"),
            ComponentType.<String>builder()
                    .codec(Codec.STRING)
                    .packetCodec(PacketCodecs.STRING)
                    .build()
    );

    /** 神匠: 攻撃力ボーナス（クラフト時に独立抽選）。 */
    public static final ComponentType<Float> DIVINE_ATTACK_DAMAGE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "divine_attack_damage"),
            ComponentType.<Float>builder().codec(Codec.FLOAT).packetCodec(PacketCodecs.FLOAT).build()
    );

    /** 神匠: 攻撃速度ボーナス（クラフト時に独立抽選）。 */
    public static final ComponentType<Float> DIVINE_ATTACK_SPEED = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "divine_attack_speed"),
            ComponentType.<Float>builder().codec(Codec.FLOAT).packetCodec(PacketCodecs.FLOAT).build()
    );

    /** 神匠: 採掘速度ボーナス（クラフト時に独立抽選）。 */
    public static final ComponentType<Float> DIVINE_MINING_SPEED = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "divine_mining_speed"),
            ComponentType.<Float>builder().codec(Codec.FLOAT).packetCodec(PacketCodecs.FLOAT).build()
    );

    /** 神匠: 防具の防御力ボーナス。 */
    public static final ComponentType<Float> DIVINE_ARMOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "divine_armor"),
            ComponentType.<Float>builder().codec(Codec.FLOAT).packetCodec(PacketCodecs.FLOAT).build()
    );

    /** 神匠: 防具の防具強度ボーナス。 */
    public static final ComponentType<Float> DIVINE_TOUGHNESS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "divine_toughness"),
            ComponentType.<Float>builder().codec(Codec.FLOAT).packetCodec(PacketCodecs.FLOAT).build()
    );

    /** プレイヤーの累計撃破数（ムラマサの100キル条件に使用）。 */
    public static final ComponentType<Integer> TOTAL_KILLS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "total_kills"),
            ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build()
    );

    /** 神匠のボーナス抽選が未完了であることを示すフラグ（インベントリ投入後に解決する）。 */
    public static final ComponentType<Boolean> PENDING_DIVINE_BONUS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(KingdomUniqueMod.MOD_ID, "pending_divine_bonus"),
            ComponentType.<Boolean>builder()
                    .codec(Codec.BOOL)
                    .packetCodec(PacketCodecs.BOOL)
                    .build()
    );

    private static boolean initialized = false;

    private ModDataComponents() {
    }

    /** ModInitializer から呼び出してクラス初期化（＝登録）を確実に行う。 */
    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        KingdomUniqueMod.LOGGER.info("[KingdomUnique] Registered data component '{}'.", ENHANCE_LEVEL);
    }
}
