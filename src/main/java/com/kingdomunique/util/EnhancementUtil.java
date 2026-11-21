package com.kingdomunique.util;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.ToolItem;
import net.minecraft.item.TridentItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 無限強化合成の計算ロジック。
 *
 * 強化段階 N に対する性能補正は 1.1^N 倍。
 * 段階を N から M に上げるときは、既に補正済みのコンポーネント値に
 * (1.1^M / 1.1^N) = 1.1^(M-N) を掛けることで累乗誤差なく更新する。
 */
public final class EnhancementUtil {

    /** ユニーク能力「神匠」のID。 */
    public static final String DIVINE_SMITH_ID = "divine_smith";

    /** 強化1段階あたりの倍率。 */
    public static final double MULTIPLIER_PER_LEVEL = 1.1D;

    /** 神匠の発動確率（0-99）。15%で+3、25%で+2、残り60%で通常の+1。 */
    private static final int DIVINE_JUMP_3_CHANCE = 15;
    private static final int DIVINE_JUMP_2_CHANCE = 40;

    /** 金床で必要な経験レベル（無限強化を可能にするため常に1）。 */
    public static final int LEVEL_COST = 1;

    private EnhancementUtil() {
    }

    /** 1.1^level を返す。 */
    public static double multiplier(int level) {
        return Math.pow(MULTIPLIER_PER_LEVEL, Math.max(0, level));
    }

    /** アイテムの強化段階を返す（未設定なら0）。 */
    public static int getLevel(ItemStack stack) {
        Integer level = stack.get(ModDataComponents.ENHANCE_LEVEL);
        return level == null ? 0 : Math.max(0, level);
    }

    /** 強化対象かどうかを判定する（武器・ツール・防具）。 */
    public static boolean isEnhanceable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        // 一点モノのユニーク武器は無限強化の対象外。
        if (stack.get(com.kingdomunique.component.ModDataComponents.UNIQUE_WEAPON_ID) != null) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof ToolItem
                || item instanceof ArmorItem
                || item instanceof RangedWeaponItem
                || item instanceof TridentItem
                || item instanceof MaceItem;
    }

    /**
     * 2つのスロットから強化結果を生成する。
     *
     * @return 条件を満たさない場合は null（バニラの金床処理に委譲する）
     */
    public static EnhancementResult tryEnhance(ItemStack left, ItemStack right, PlayerEntity player) {
        return tryEnhance(left, right, player, true);
    }

    /**
     * 2つのスロットから強化結果を生成する。
     *
     * @param rollBonus true なら神匠の確率抽選を行う。false なら +1 固定の仮プレビュー
     *                  （リセマラ防止のため updateResult 側では確率を振らない）。
     */
    public static EnhancementResult tryEnhance(ItemStack left, ItemStack right, PlayerEntity player,
                                               boolean rollBonus) {
        if (!isEnhanceable(left) || !isEnhanceable(right)) {
            return null;
        }
        if (left.getItem() != right.getItem()) {
            return null;
        }

        int currentLevel = getLevel(left);
        if (currentLevel != getLevel(right)) {
            return null;
        }

        int increment = rollBonus && player instanceof ServerPlayerEntity serverPlayer
                ? computeIncrement(serverPlayer)
                : 1;
        int targetLevel = currentLevel + increment;

        ItemStack result = applyLevel(left, currentLevel, targetLevel);
        return new EnhancementResult(result, LEVEL_COST, increment > 1, targetLevel);
    }

    /**
     * 強化段階を currentLevel から targetLevel へ変更した新しい ItemStack を返す。
     */
    public static ItemStack applyLevel(ItemStack source, int currentLevel, int targetLevel) {
        ItemStack result = source.copy();
        result.setCount(1);
        result.set(ModDataComponents.ENHANCE_LEVEL, targetLevel);

        double factor = multiplier(targetLevel - currentLevel);

        AttributeModifiersComponent modifiers = result.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            List<AttributeModifiersComponent.Entry> scaled = new ArrayList<>();
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                EntityAttributeModifier old = entry.modifier();
                double newValue;
                if (old.operation() == EntityAttributeModifier.Operation.ADD_VALUE && old.value() < 0.0D) {
                    // 攻撃速度のような負の加算補正は、値を掛けるとさらに遅くなる。
                    // 絶対値を 1.1^N で割ることで実効値が 1.1^N 倍になるようにする。
                    newValue = old.value() / factor;
                } else {
                    newValue = old.value() * factor;
                }
                EntityAttributeModifier updated = new EntityAttributeModifier(
                        old.id(), newValue, old.operation());
                scaled.add(new AttributeModifiersComponent.Entry(entry.attribute(), updated, entry.slot()));
            }
            result.set(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                    new AttributeModifiersComponent(scaled, modifiers.showInTooltip()));
        }

        // 1.21.1 のツール採掘速度は AttributeModifiers ではなく TOOL コンポーネントで定義される。
        net.minecraft.component.type.ToolComponent tool = result.get(DataComponentTypes.TOOL);
        if (tool != null) {
            float miningFactor = (float) factor * (currentLevel == 0 && targetLevel > 0 ? 2.0F : 1.0F);
            List<net.minecraft.component.type.ToolComponent.Rule> scaledRules = new ArrayList<>();
            for (net.minecraft.component.type.ToolComponent.Rule rule : tool.rules()) {
                scaledRules.add(new net.minecraft.component.type.ToolComponent.Rule(
                        rule.blocks(),
                        rule.speed().map(speed -> speed * miningFactor),
                        rule.correctForDrops()));
            }
            result.set(DataComponentTypes.TOOL, new net.minecraft.component.type.ToolComponent(
                    scaledRules,
                    tool.defaultMiningSpeed() * miningFactor,
                    tool.damagePerBlock()));
        }

        Integer maxDamage = result.get(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage != null && maxDamage > 0) {
            int scaledMax = Math.max(1, (int) Math.round(maxDamage * factor));
            result.set(DataComponentTypes.MAX_DAMAGE, scaledMax);
        }

        // Gemini 修正: 既存の称号・「 +数字」をすべて剥がしてベース名を取り出す
        // （「+5 +6 +7」や称号の重複増殖を防ぐ）。
        Text existingName = result.get(DataComponentTypes.CUSTOM_NAME);
        String plainName = existingName == null ? "" : existingName.getString();
        plainName = plainName.replaceAll("^【[^】]+】\\s*", "")
                .replaceAll("^\\[[^\\]]+\\]\\s*", "")
                .replaceAll("(?:\\s*\\+\\d+)+$", "")
                .trim();
        if (plainName.isEmpty()) {
            plainName = result.getItem().getName().getString();
        }

        // 強化段階に応じた称号とカラー（名工=水色 / 英雄級=紫+水色 / 神話級=紫）。
        String titlePrefix = "";
        Formatting titleColor = Formatting.WHITE;
        Formatting plusColor = Formatting.AQUA;
        if (targetLevel >= 15) {
            titlePrefix = "【神話級】";
            titleColor = Formatting.LIGHT_PURPLE;
            plusColor = Formatting.LIGHT_PURPLE;
        } else if (targetLevel >= 10) {
            titlePrefix = "【英雄級】";
            titleColor = Formatting.LIGHT_PURPLE;
            plusColor = Formatting.AQUA;
        } else if (targetLevel >= 5) {
            titlePrefix = "【名工】";
            titleColor = Formatting.AQUA;
            plusColor = Formatting.AQUA;
        }

        // 斜体（イタリック）を明示的に無効化して組み立てる。
        MutableText finalName = Text.empty().styled(style -> style.withItalic(false));
        if (!titlePrefix.isEmpty()) {
            finalName.append(Text.literal(titlePrefix).formatted(titleColor));
        }
        finalName.append(Text.literal(plainName).formatted(Formatting.WHITE));
        finalName.append(Text.literal(" +" + targetLevel).formatted(plusColor));
        result.set(DataComponentTypes.CUSTOM_NAME, finalName);

        return result;
    }

    /** 神匠ボーナスを含めた上昇段階数を返す（取り出し時に確定させるため公開）。 */
    public static int computeIncrement(ServerPlayerEntity player) {
        if (!hasDivineSmith(player)) {
            return 1;
        }
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < DIVINE_JUMP_3_CHANCE) {
            return 3;
        }
        if (roll < DIVINE_JUMP_2_CHANCE) {
            return 2;
        }
        return 1;
    }

    /** プレイヤーが「神匠」を所持しているかを永続データから判定する。 */
    private static boolean hasDivineSmith(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            return false;
        }
        WorldUniqueData data = WorldUniqueData.getOrCreate(overworld);
        return DIVINE_SMITH_ID.equals(data.getClaimedId(player.getUuid()));
    }

    /** 神匠のクラフトボーナス。アイテム種別ごとに渡された値のみを反映する。 */
    public static void applyDivineBonuses(ItemStack stack, float attack, float speed, float mining,
                                          float armor, float toughness) {
        Identifier attackId = Identifier.of("kingdomunique", "divine_attack_damage");
        Identifier speedId = Identifier.of("kingdomunique", "divine_attack_speed");
        Identifier miningId = Identifier.of("kingdomunique", "divine_mining_speed");
        Identifier breakSpeedId = Identifier.of("kingdomunique", "divine_block_break_speed");
        Identifier armorId = Identifier.of("kingdomunique", "divine_armor");
        Identifier toughnessId = Identifier.of("kingdomunique", "divine_armor_toughness");
        List<Identifier> ids = List.of(attackId, speedId, miningId, breakSpeedId, armorId, toughnessId);

        stack.set(ModDataComponents.DIVINE_ATTACK_DAMAGE, attack);
        stack.set(ModDataComponents.DIVINE_ATTACK_SPEED, speed);
        stack.set(ModDataComponents.DIVINE_MINING_SPEED, mining);
        stack.set(ModDataComponents.DIVINE_ARMOR, armor);
        stack.set(ModDataComponents.DIVINE_TOUGHNESS, toughness);

        AttributeModifiersComponent current = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        List<AttributeModifiersComponent.Entry> entries = new ArrayList<>();
        if (current != null) {
            for (AttributeModifiersComponent.Entry entry : current.modifiers()) {
                if (!ids.contains(entry.modifier().id())) {
                    entries.add(entry);
                }
            }
        }
        EntityAttributeModifier.Operation op = EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        AttributeModifierSlot handSlot = AttributeModifierSlot.MAINHAND;
        addIfPositive(entries, EntityAttributes.GENERIC_ATTACK_DAMAGE, attackId, attack, op, handSlot);
        addIfPositive(entries, EntityAttributes.GENERIC_ATTACK_SPEED, speedId, speed, op, handSlot);
        // 採掘ツール: 採掘効率とブロック破壊速度の両方をメインハンドに付与する。
        addIfPositive(entries, EntityAttributes.PLAYER_MINING_EFFICIENCY, miningId, mining, op, handSlot);
        addIfPositive(entries, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, breakSpeedId, mining, op, handSlot);

        // 防具: 元の装備スロットを維持して防御力／防具強度を加算する。
        AttributeModifierSlot armorSlot = handSlot;
        if (stack.getItem() instanceof ArmorItem armorItem) {
            armorSlot = AttributeModifierSlot.forEquipmentSlot(armorItem.getSlotType());
        }
        addIfPositive(entries, EntityAttributes.GENERIC_ARMOR, armorId, armor, op, armorSlot);
        addIfPositive(entries, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, toughnessId, toughness, op, armorSlot);

        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                new AttributeModifiersComponent(entries, current == null || current.showInTooltip()));

        // TOOLコンポーネントの採掘速度も引き上げる（採掘ツールのみ）。
        net.minecraft.component.type.ToolComponent tool = stack.get(DataComponentTypes.TOOL);
        if (tool != null && mining > 0.0F) {
            List<net.minecraft.component.type.ToolComponent.Rule> rules = new ArrayList<>();
            for (net.minecraft.component.type.ToolComponent.Rule rule : tool.rules()) {
                rules.add(new net.minecraft.component.type.ToolComponent.Rule(
                        rule.blocks(),
                        rule.speed().map(value -> value * 2.0F * (1.0F + mining)),
                        rule.correctForDrops()));
            }
            stack.set(DataComponentTypes.TOOL, new net.minecraft.component.type.ToolComponent(
                    rules, tool.defaultMiningSpeed() * 2.0F * (1.0F + mining), tool.damagePerBlock()));
        }
    }

    private static void addIfPositive(List<AttributeModifiersComponent.Entry> entries,
                                      net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                                      Identifier id, float value,
                                      EntityAttributeModifier.Operation operation,
                                      AttributeModifierSlot slot) {
        if (value > 0.0F) {
            entries.add(new AttributeModifiersComponent.Entry(
                    attribute, new EntityAttributeModifier(id, value, operation), slot));
        }
    }

    /** 殺戮の宝剣の攻撃力ボーナスをコンポーネントへ反映する。 */
    public static void applySlaughterBonus(ItemStack stack, float bonus) {
        net.minecraft.component.type.AttributeModifiersComponent current =
                stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        java.util.List<net.minecraft.component.type.AttributeModifiersComponent.Entry> entries =
                new java.util.ArrayList<>();
        if (current != null) {
            entries.addAll(current.modifiers());
        }
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of("kingdomunique", "slaughter_bonus");
        entries.removeIf(entry -> entry.modifier().id().equals(id));
        entries.add(new net.minecraft.component.type.AttributeModifiersComponent.Entry(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(id, bonus, EntityAttributeModifier.Operation.ADD_VALUE),
                net.minecraft.component.type.AttributeModifierSlot.MAINHAND
        ));
        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                new net.minecraft.component.type.AttributeModifiersComponent(
                        entries, current == null || current.showInTooltip()));
    }

    /**
     * 奇術の防具ブースト。AnimalArmorItem は ArmorItem のサブクラスなので
     * 通常の防具と同じくここで防御力・耐久性（GENERIC_ARMOR /
     * GENERIC_ARMOR_TOUGHNESS / MAX_DAMAGE）を更新できる。
     */
    public static void boostArmor(ItemStack stack, double factor) {
        if (!(stack.getItem() instanceof ArmorItem)) {
            return;
        }
        AttributeModifiersComponent current = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (current != null && !current.modifiers().isEmpty()) {
            List<AttributeModifiersComponent.Entry> scaled = new ArrayList<>();
            for (AttributeModifiersComponent.Entry entry : current.modifiers()) {
                boolean armorAttribute = entry.attribute() == EntityAttributes.GENERIC_ARMOR
                        || entry.attribute() == EntityAttributes.GENERIC_ARMOR_TOUGHNESS;
                double value = armorAttribute ? entry.modifier().value() * factor : entry.modifier().value();
                EntityAttributeModifier updated = new EntityAttributeModifier(
                        entry.modifier().id(), value, entry.modifier().operation());
                scaled.add(new AttributeModifiersComponent.Entry(
                        entry.attribute(), updated, entry.slot()));
            }
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                    new AttributeModifiersComponent(scaled, current.showInTooltip()));
        }

        Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage != null && maxDamage > 0) {
            stack.set(DataComponentTypes.MAX_DAMAGE,
                    Math.max(1, (int) Math.round(maxDamage * factor)));
        }
    }

    /** 強化結果。 */
    public record EnhancementResult(ItemStack stack, int levelCost, boolean divineBonus, int newLevel) {
    }
}
