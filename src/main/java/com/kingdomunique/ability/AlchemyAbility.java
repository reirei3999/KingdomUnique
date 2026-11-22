package com.kingdomunique.ability;

import com.kingdomunique.data.AbilityUsageData;
import com.kingdomunique.registry.UniqueContentRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.Map;

/** 錬金術：手に持ったブロック1個を1段上の素材に変える（1日の回数に上限あり）。 */
public final class AlchemyAbility {

    public static final int DAILY_LIMIT = 10;

    private static final Map<Item, Item> UPGRADES = Map.ofEntries(
            Map.entry(Items.STONE, Items.IRON_ORE),
            Map.entry(Items.COBBLESTONE, Items.IRON_ORE),
            Map.entry(Items.COAL_ORE, Items.IRON_ORE),
            Map.entry(Items.COPPER_ORE, Items.IRON_ORE),
            Map.entry(Items.IRON_ORE, Items.GOLD_ORE),
            Map.entry(Items.GOLD_ORE, Items.DIAMOND_ORE),
            Map.entry(Items.DEEPSLATE, Items.DEEPSLATE_IRON_ORE),
            Map.entry(Items.COBBLED_DEEPSLATE, Items.DEEPSLATE_IRON_ORE),
            Map.entry(Items.DEEPSLATE_COAL_ORE, Items.DEEPSLATE_IRON_ORE),
            Map.entry(Items.DEEPSLATE_COPPER_ORE, Items.DEEPSLATE_IRON_ORE),
            Map.entry(Items.DEEPSLATE_IRON_ORE, Items.DEEPSLATE_GOLD_ORE),
            Map.entry(Items.DEEPSLATE_GOLD_ORE, Items.DEEPSLATE_DIAMOND_ORE),
            Map.entry(Items.COAL_BLOCK, Items.IRON_BLOCK),
            Map.entry(Items.COPPER_BLOCK, Items.IRON_BLOCK),
            Map.entry(Items.IRON_BLOCK, Items.GOLD_BLOCK),
            Map.entry(Items.GOLD_BLOCK, Items.DIAMOND_BLOCK)
    );

    private AlchemyAbility() {
    }

    public static Item upgradeOf(Item item) {
        return UPGRADES.get(item);
    }

    public static long currentDay(ServerWorld world) {
        ServerWorld overworld = world.getServer().getWorld(World.OVERWORLD);
        return (overworld == null ? world : overworld).getTimeOfDay() / 24000L;
    }

    public static int remainingUses(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        return Math.max(0, DAILY_LIMIT - usage(player).getUses(player.getUuid(),
                UniqueContentRegistry.ALCHEMY, currentDay(world)));
    }

    public static boolean activate(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        Item result = held.isEmpty() ? null : upgradeOf(held.getItem());
        if (result == null) {
            player.sendMessage(Text.literal("§c[錬金術] 変換できるブロックを手に持ってください（例：石・鉱石・鉱石ブロック）。"), true);
            return false;
        }
        ServerWorld world = player.getServerWorld();
        long day = currentDay(world);
        AbilityUsageData data = usage(player);
        if (data.getUses(player.getUuid(), UniqueContentRegistry.ALCHEMY, day) >= DAILY_LIMIT) {
            player.sendMessage(Text.literal("§c[錬金術] 本日の使用回数（" + DAILY_LIMIT + "回）を使い切りました。"), true);
            return false;
        }

        String from = held.getName().getString();
        ItemStack converted = new ItemStack(result);
        String to = converted.getName().getString();
        if (held.getCount() == 1) {
            player.setStackInHand(Hand.MAIN_HAND, converted);
        } else {
            held.decrement(1);
            player.getInventory().offerOrDrop(converted);
        }
        int used = data.addUse(player.getUuid(), UniqueContentRegistry.ALCHEMY, day);
        world.spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getEyeY(), player.getZ(),
                30, 0.5D, 0.5D, 0.5D, 0.5D);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                SoundCategory.PLAYERS, 1.0F, 1.2F);
        player.sendMessage(Text.literal("§6[錬金術] " + from + " → " + to
                + "（本日の残り " + (DAILY_LIMIT - used) + "回）"), true);
        return true;
    }

    private static AbilityUsageData usage(ServerPlayerEntity player) {
        return AbilityUsageData.getOrCreate(player.getServer().getWorld(World.OVERWORLD));
    }
}
