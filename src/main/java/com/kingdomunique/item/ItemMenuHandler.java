package com.kingdomunique.item;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.network.OpenItemMenuPayload;
import com.kingdomunique.network.RequestItemPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.UUID;

/** 専用アイテムメニューのサーバー側処理と /ku menu の登録。 */
public final class ItemMenuHandler {

    private ItemMenuHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                com.kingdomunique.network.RequestOpenItemMenuPayload.ID, (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (!isAdmin(player)) {
                        player.sendMessage(Text.literal("§c[KingdomUnique] このメニューは管理者のみ使用できます。"), false);
                        return;
                    }
                    openMenu(player);
                });

        ServerPlayNetworking.registerGlobalReceiver(
                com.kingdomunique.network.RequestBlueprintInfoPayload.ID, (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    net.minecraft.server.world.ServerWorld world = player.getServerWorld();
                    if (!world.isChunkLoaded(payload.pos())) {
                        return;
                    }
                    if (!(world.getBlockEntity(payload.pos()) instanceof com.kingdomunique.building.BlueprintBlockEntity be)) {
                        return;
                    }
                    if (!(world.getBlockState(payload.pos()).getBlock()
                            instanceof com.kingdomunique.building.BlueprintBlock block)) {
                        return;
                    }
                    com.kingdomunique.building.BuildingType type = block.getType();
                    double multiplier = com.kingdomunique.building.BuildingManager.costMultiplier(player);
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    for (java.util.Map.Entry<net.minecraft.item.Item, Integer> entry : type.materials().entrySet()) {
                        int needed = Math.max(1, (int) Math.ceil(entry.getValue() * multiplier));
                        int have = be.countOf(entry.getKey());
                        lines.add(entry.getKey().getName().getString() + " " + have + "/" + needed);
                    }
                    lines.add(be.isReady() ? "状態: 建築中" : "状態: 資材待ち");
                    ServerPlayNetworking.send(player, new com.kingdomunique.network.BlueprintInfoPayload(
                            payload.pos(), type.getDisplayName(), type.getBuildRadius(), lines));
                });

        ServerPlayNetworking.registerGlobalReceiver(RequestItemPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!isAdmin(player)) {
                player.sendMessage(Text.literal("§c[KingdomUnique] このメニューは管理者のみ使用できます。"), false);
                return;
            }
            give(player, payload.itemId());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ku")
                    .then(CommandManager.literal("menu")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                openMenu(player);
                                return 1;
                            }))
                    .then(CommandManager.literal("debug_village")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player == null) {
                                    return 0;
                                }
                                net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0F);
                                int x = (int) (player.getX() + look.x * 8.0D);
                                int z = (int) (player.getZ() + look.z * 8.0D);
                                com.kingdomunique.world.VillageBuilder.generateStarterVillage(
                                        player.getServerWorld(), x, z);
                                player.sendMessage(Text.literal("§6[KingdomUnique] 目の前に村を生成しました。"), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("enhance")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                    .executes(context -> {
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        if (player == null) {
                                            return 0;
                                        }
                                        int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level");
                                        return enhanceHeld(player, level);
                                    })))
                    .then(CommandManager.literal("give_enhanced")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("item", net.minecraft.command.argument.ItemStackArgumentType.itemStack(registryAccess))
                                    .then(CommandManager.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100))
                                            .executes(context -> {
                                                ServerPlayerEntity player = context.getSource().getPlayer();
                                                if (player == null) {
                                                    return 0;
                                                }
                                                net.minecraft.command.argument.ItemStackArgument arg = net.minecraft.command.argument.ItemStackArgumentType.getItemStackArgument(context, "item");
                                                int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level");
                                                ItemStack stack = arg.createStack(1, false);
                                                if (level > 0) {
                                                    stack = com.kingdomunique.util.EnhancementUtil.applyLevel(stack, 0, level);
                                                }
                                                player.giveItemStack(stack);
                                                player.sendMessage(Text.literal("§6[KingdomUnique] +" + level + " のアイテムを付与しました。"), false);
                                                return 1;
                                            })))));
        });
    }

    private static boolean isAdmin(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2);
    }

    private static int enhanceHeld(ServerPlayerEntity player, int level) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            player.sendMessage(Text.literal("§c[KingdomUnique] 手にアイテムを持ってください。"), false);
            return 0;
        }
        if (!com.kingdomunique.util.EnhancementUtil.isEnhanceable(stack)) {
            player.sendMessage(Text.literal("§c[KingdomUnique] このアイテムは強化できません。"), false);
            return 0;
        }
        int current = com.kingdomunique.util.EnhancementUtil.getLevel(stack);
        ItemStack enhanced = com.kingdomunique.util.EnhancementUtil.applyLevel(stack, current, level);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, enhanced);
        player.sendMessage(Text.literal("§6[KingdomUnique] 強化段階を +" + level + " に設定しました。"), false);
        return 1;
    }

    private static void openMenu(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, OpenItemMenuPayload.ID)) {
            ServerPlayNetworking.send(player, new OpenItemMenuPayload());
        } else {
            player.sendMessage(Text.literal("§c[KingdomUnique] クライアントがメニューに対応していません。"), false);
        }
    }

    private static void give(ServerPlayerEntity player, String id) {
        ItemStack stack = ItemCatalog.create(id);
        if (stack.isEmpty()) {
            return;
        }
        // 管理者メニューからは所有者に関係なく付与する（排他リストは変更しない）。
        player.giveItemStack(stack);
        player.sendMessage(Text.literal("§6[KingdomUnique] " + stack.getName().getString() + " を入手しました。"), false);
    }
}
