package com.kingdomunique.mixin;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** ユニーク武器・殺戮の宝剣・無限強化アイテムの詳細ツールチップ。 */
@Mixin(Item.class)
public abstract class ItemTooltipMixin {

    @Inject(method = "appendTooltip", at = @At("RETURN"))
    private void kingdomUnique$appendTooltips(ItemStack stack, Item.TooltipContext context,
                                              List<Text> tooltip, TooltipType type, CallbackInfo ci) {
        if (stack.isEmpty()) {
            return;
        }

        boolean relicOrCurseWeapon = stack.isOf(ModItems.SLAUGHTER_BLADE)
                || stack.isOf(ModItems.DESTRUCTION_PICKAXE);
        String uniqueId = stack.get(ModDataComponents.UNIQUE_WEAPON_ID);
        if (uniqueId != null && !relicOrCurseWeapon) {
            appendUniqueWeapon(tooltip, uniqueId, stack);
        }
        if (stack.isOf(ModItems.SLAUGHTER_BLADE)) {
            appendSlaughterBlade(tooltip, stack);
        }
        if (stack.isOf(ModItems.DESTRUCTION_PICKAXE)) {
            tooltip.add(Text.literal(""));
            tooltip.add(Text.literal("【呪物（カース）】").formatted(Formatting.DARK_RED, Formatting.BOLD));
            tooltip.add(Text.literal("  どんな硬いブロックも一瞬で砕く。")
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
            tooltip.add(Text.literal("  破壊したブロックのドロップは消滅する")
                    .formatted(Formatting.RED));
        }

        float dmg = stack.getOrDefault(ModDataComponents.DIVINE_ATTACK_DAMAGE, 0.0F);
        float spd = stack.getOrDefault(ModDataComponents.DIVINE_ATTACK_SPEED, 0.0F);
        float mine = stack.getOrDefault(ModDataComponents.DIVINE_MINING_SPEED, 0.0F);
        float armorBonus = stack.getOrDefault(ModDataComponents.DIVINE_ARMOR, 0.0F);
        float toughBonus = stack.getOrDefault(ModDataComponents.DIVINE_TOUGHNESS, 0.0F);
        if (dmg > 0.0F || spd > 0.0F || mine > 0.0F || armorBonus > 0.0F || toughBonus > 0.0F) {
            tooltip.add(Text.literal(""));
            tooltip.add(Text.literal("【神匠の加護】").formatted(Formatting.GOLD, Formatting.BOLD));
            if (dmg > 0.0F) {
                tooltip.add(Text.literal("  神匠ボーナス：攻撃力 +" + percent(dmg))
                        .formatted(Formatting.YELLOW));
            }
            if (spd > 0.0F) {
                tooltip.add(Text.literal("  神匠ボーナス：攻撃速度 +" + percent(spd))
                        .formatted(Formatting.AQUA));
            }
            if (mine > 0.0F) {
                tooltip.add(Text.literal("  神匠ボーナス：採掘速度 +" + percent(mine))
                        .formatted(Formatting.GREEN));
            }
            if (armorBonus > 0.0F) {
                tooltip.add(Text.literal("  神匠ボーナス：防御力 +" + percent(armorBonus))
                        .formatted(Formatting.AQUA));
            }
            if (toughBonus > 0.0F) {
                tooltip.add(Text.literal("  神匠ボーナス：防具強度 +" + percent(toughBonus))
                        .formatted(Formatting.LIGHT_PURPLE));
            }
        }

        int level = stack.getOrDefault(ModDataComponents.ENHANCE_LEVEL, 0);
        if (level >= 1) {
            tooltip.add(Text.literal(""));
            tooltip.add(Text.literal("◆ 無限強化 +" + level + "（基本性能・耐久値 1.1^"
                    + level + "倍）").formatted(Formatting.AQUA));
        }

        // アイテム名の色分け: ユニーク武器=金+太字、遺物=水色、呪物=暗赤色+太字。
        if (!tooltip.isEmpty()) {
            Text name = tooltip.get(0);
            if (stack.isOf(ModItems.SLAUGHTER_BLADE)) {
                tooltip.set(0, name.copy().styled(s -> s.withColor(Formatting.AQUA)
                        .withItalic(false)));
            } else if (stack.isOf(ModItems.DESTRUCTION_PICKAXE)) {
                tooltip.set(0, name.copy().styled(s -> s.withColor(Formatting.DARK_RED)
                        .withBold(true).withItalic(false)));
            } else if (uniqueId != null) {
                tooltip.set(0, name.copy().styled(s -> s.withColor(Formatting.GOLD)
                        .withBold(true).withItalic(false)));
            }
        }
    }

    private static String percent(float value) {
        return String.format("%.1f", value * 100.0F) + "%";
    }

    private static void appendUniqueWeapon(List<Text> tooltip, String id, ItemStack stack) {
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("【神話級ユニーク武器】").formatted(Formatting.GOLD, Formatting.BOLD));
        tooltip.add(Text.literal(flavor(id)).formatted(Formatting.GRAY, Formatting.ITALIC));

        String[] lines = lines(id, stack);
        for (String line : lines) {
            if (line.isEmpty()) {
                tooltip.add(Text.literal(""));
            } else if (line.startsWith("!")) {
                tooltip.add(Text.literal("  " + line.substring(1)).formatted(Formatting.RED));
            } else if (line.startsWith("~")) {
                tooltip.add(Text.literal("  " + line.substring(1)).formatted(Formatting.AQUA));
            } else if (line.startsWith("*")) {
                tooltip.add(Text.literal("  " + line.substring(1)).formatted(Formatting.GREEN));
            } else if (line.startsWith("#")) {
                tooltip.add(Text.literal("  " + line.substring(1)).formatted(Formatting.YELLOW));
            } else {
                tooltip.add(Text.literal("  " + line).formatted(Formatting.GRAY));
            }
        }
        tooltip.add(Text.literal("§8世界に一つだけの存在").formatted(Formatting.DARK_GRAY));
    }

    private static void appendSlaughterBlade(List<Text> tooltip, ItemStack stack) {
        float bonus = stack.getOrDefault(ModDataComponents.SLAUGHTER_BONUS, 0.0F);
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("【遺物（レリック）】").formatted(Formatting.AQUA, Formatting.BOLD));
        tooltip.add(Text.literal("倒した敵の数だけ剣が研ぎ澄まされる。")
                .formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.literal("  現在の攻撃力ボーナス: +" + String.format("%.2f", bonus))
                .formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  中立・敵対Mob撃破ごとに +0.05 ずつ永久上昇").formatted(Formatting.GREEN));
    }

    private static String flavor(String id) {
        return switch (id) {
            case "excalibur" -> "王の威光を宿す、選ばれし者の剣。";
            case "gae_bolg" -> "投げられし魔槍は、必ず獲物を追う。";
            case "muramasa" -> "飲み干すほどに強くなる、妖の刀。";
            case "apollo_bow" -> "太陽の光そのもので編まれた弓。";
            case "mjolnir" -> "雷を筆として、空に一撃を描く。";
            case "chronos_dagger" -> "時を切り裂く、瞬きほどの短剣。";
            default -> "世界で唯一の武器。";
        };
    }

    private static String[] lines(String id, ItemStack stack) {
        return switch (id) {
            case "excalibur" -> new String[]{
                    "#王国領地内での攻撃力 ×1.8",
                    "~領地半径100マス（王城で200マス）",
                    "~周囲15マスの自国兵士に攻撃力II・再生I",
                    "近接武器として使用"
            };
            case "gae_bolg" -> new String[]{
                    "#右クリック: 魔槍を高速投擲",
                    "~防御貫通の特大ダメージ",
                    "#投擲キルでクールタイム 36秒 → 18秒",
                    "通常クールタイム: 36秒"
            };
            case "muramasa" -> new String[]{
                    "#与ダメージの20%をHP回復",
                    "*撃破ごとに攻撃力 +0.35（永久）",
                    "#現在の攻撃力ボーナス: +" + String.format("%.2f",
                            stack.getOrDefault(ModDataComponents.SLAUGHTER_BONUS, 0.0F)),
                    "!メインハンド保持中は空腹の減少がわずかに増加"
            };
            case "apollo_bow" -> new String[]{
                    "#左クリック：光の矢発射（CT 8秒）",
                    "#右クリック：空中起爆",
                    "~アンデッド系にダメージ2倍",
                    "~爆発: 範囲炎上＋燃焼・盲目付与"
            };
            case "mjolnir" -> new String[]{
                    "#右クリック: 雷弾を直線レイキャスト",
                    "~高威力固定ダメージ＋着弾点に落雷",
                    "CT 25秒"
            };
            case "chronos_dagger" -> new String[]{
                    "#非常に速い攻撃速度",
                    "~攻撃ヒット20%で敵を0.5秒完全停止",
                    "停止中はAI・移動がロックされる"
            };
            default -> new String[]{"#世界で唯一の強力な武器"};
        };
    }
}
