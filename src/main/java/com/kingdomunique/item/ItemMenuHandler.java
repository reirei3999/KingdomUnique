package com.kingdomunique.item;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.network.OpenItemMenuPayload;
import com.kingdomunique.network.RequestItemPayload;
import com.kingdomunique.network.ItemGrantResultPayload;
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
        var resultType = ItemGrantResultPayload.TYPE;
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
                    if (be.isReady()) {
                        // 建築開始後は資材が消費されて空になるため、
                        // 「0/N（資材待ち）」ではなく「建設中」として表示する。
                        lines.add("状態: 建設中");
                        com.kingdomunique.building.BuildData buildData =
                                com.kingdomunique.building.BuildData.getOrCreate(world);
                        for (com.kingdomunique.building.BuildData.BuildSite site
                                : buildData.sites()) {
                            if (!site.pos().equals(payload.pos())) {
                                continue;
                            }
                            int required = com.kingdomunique.building.BuildingManager
                                    .requiredTicks(world, site);
                            int remaining = Math.max(0, required - site.progress());
                            int percent = required > 0
                                    ? Math.min(100, (int) (site.progress() * 100L / required)) : 0;
                            lines.add("残り時間: " + String.format("%.1f", remaining / 20.0F) + "秒");
                            lines.add("進捗: " + percent + "%");
                            break;
                        }
                    } else {
                        for (java.util.Map.Entry<net.minecraft.item.Item, Integer> entry
                                : type.materials().entrySet()) {
                            int needed = Math.max(1, (int) Math.ceil(entry.getValue() * multiplier));
                            int have = be.countOf(entry.getKey());
                            lines.add(entry.getKey().getName().getString() + " " + have + "/" + needed);
                        }
                        lines.add(be.hasAllMaterials(type, multiplier) ? "状態: 資材投入済み（未着工）" : "状態: 資材待ち");
                        int estimate = (int) Math.max(20L,
                                type.getBaseBuildTicks() * multiplier);
                        lines.add("予想建築時間: " + String.format("%.1f", estimate / 20.0F) + "秒");
                    }
                    ServerPlayNetworking.send(player, new com.kingdomunique.network.BlueprintInfoPayload(
                            payload.pos(), type.getDisplayName(), type.getBuildRadius(), lines));
                });

        ServerPlayNetworking.registerGlobalReceiver(RequestItemPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!isAdmin(player)) {
                reject(player, payload.itemId(), "管理者のみ使用できます。管理者に権限を確認してください。");
                return;
            }
            give(player, payload.itemId());
        });

    }

    /** /ku に管理者用サブコマンドを追加する（ModCommands から1度だけ呼ばれる）。 */
    public static void registerCommands(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> ku,
            net.minecraft.command.CommandRegistryAccess registryAccess) {
        ku
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
                                            }))));
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
        if (stack.isEmpty() || stack.getItem() == net.minecraft.item.Items.AIR) {
            reject(player, id, "アイテムが見つかりません。メニューを開き直してください。");
            return;
        }
        if (stack.getCount() <= 0) {
            stack.setCount(1);
        }
        String itemName = ItemCatalog.entries().stream().filter(entry -> entry.id().equals(id))
                .findFirst().orElseThrow().label();
        int quantity = stack.getCount();
        int capacity = 0;
        for (ItemStack slot : player.getInventory().main) {
            if (slot.isEmpty()) {
                capacity += stack.getMaxCount();
            } else if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                capacity += Math.max(0, slot.getMaxCount() - slot.getCount());
            }
        }
        if (capacity < quantity) {
            reject(player, id, "所持品がいっぱいです。空きを作ってからもう一度クリック");
            return;
        }
        // 管理者メニューからは所有者に関係なく付与する（排他リストは変更しない）。
        player.getInventory().insertStack(stack);
        player.currentScreenHandler.sendContentUpdates();
        ServerPlayNetworking.send(player, new ItemGrantResultPayload(id, true, itemName, quantity, ""));
        player.sendMessage(Text.literal("[KingdomUnique] " + itemName + " ×" + quantity + " を入手"), false);
    }

    private static void reject(ServerPlayerEntity player, String id, String reason) {
        ServerPlayNetworking.send(player, new ItemGrantResultPayload(id, false, "", 0, reason));
        player.sendMessage(Text.literal("[KingdomUnique] " + reason), false);
    }
}
