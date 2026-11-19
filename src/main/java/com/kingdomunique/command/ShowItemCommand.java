package com.kingdomunique.command;

import com.kingdomunique.util.EnhancementUtil;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

/**
 * /i コマンド。
 *
 * メインハンドのアイテムを、ホバーテキスト付きの Text コンポーネントとして
 * サーバー内の全プレイヤーのチャットへ披露する。
 */
public final class ShowItemCommand {

    private ShowItemCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(net.minecraft.server.command.CommandManager.literal("i")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    return showItem(player);
                }));
    }

    private static int showItem(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            player.sendMessage(Text.literal("§c[KingdomUnique] 披露するアイテムをメインハンドに持ってください。"), false);
            return 0;
        }

        Text hover = buildHoverText(stack);
        Text itemName = stack.getName().copy().setStyle(Style.EMPTY
                .withColor(Formatting.GOLD)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));

        Text message = Text.literal("")
                .append(player.getDisplayName())
                .append(Text.literal(" §7が披露: §r"))
                .append(itemName);

        if (player.getServer() != null) {
            for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
                target.sendMessage(message, false);
            }
        }
        return 1;
    }

    /** ホバー時に表示される詳細テキストを組み立てる。 */
    private static Text buildHoverText(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        int level = EnhancementUtil.getLevel(stack);

        lines.add("§6§l===== アイテム情報 =====");
        lines.add("§7名前: §f" + stack.getName().getString());
        lines.add("§7ID: §f" + net.minecraft.registry.Registries.ITEM.getId(stack.getItem()));
        lines.add("§7強化段階: §a+" + level);
        lines.add("§7強化倍率: §f×" + String.format(java.util.Locale.ROOT, "%.3f", EnhancementUtil.multiplier(level)));

        Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
        lines.add("§7最大耐久: §f" + (maxDamage == null ? "なし" : maxDamage));

        Integer damage = stack.get(DataComponentTypes.DAMAGE);
        lines.add("§7現在の損耗: §f" + (damage == null ? 0 : damage));

        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            lines.add("§7属性補正:");
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                lines.add("  §f" + entry.modifier().id().getPath()
                        + " = " + String.format(java.util.Locale.ROOT, "%.3f", entry.modifier().value())
                        + " §7(" + entry.modifier().operation().name() + ")");
            }
        }

        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            lines.add("§7エンチャント:");
            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
                Text name = Enchantment.getName(entry.getKey(), entry.getIntValue());
                lines.add("  §f" + name.getString());
            }
        } else {
            lines.add("§7エンチャント: §fなし");
        }

        lines.add("§6§l======================");
        return Text.literal(String.join("\n", lines)).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
    }
}
