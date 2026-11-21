package com.kingdomunique.selection;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.effect.CurseEffects;
import com.kingdomunique.effect.RelicEffects;
import com.kingdomunique.network.SelectionChoicePayload;
import com.kingdomunique.registry.ContentRegistry;
import com.kingdomunique.registry.CurseRegistry;
import com.kingdomunique.registry.RelicRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** ステップ4のトリガー・効果イベント・コマンドをまとめて登録する。 */
public final class SelectionEventHandler {

    private static final long GLUTTONY_TICKS = 200L;
    private static final Map<UUID, Long> GLUTTONY_UNTIL = new ConcurrentHashMap<>();
    private static final long DAY = 24000L;

    private SelectionEventHandler() {
    }

    /** /ku に選択・外交サブコマンドを追加する（ModCommands から1度だけ呼ばれる）。 */
    public static void registerCommands(LiteralArgumentBuilder<ServerCommandSource> ku) {
        ku
                    .then(manualSelection("select", ContentRegistry.Kind.ABILITY)
                            .then(manualSelection("ability", ContentRegistry.Kind.ABILITY))
                            .then(manualSelection("relic", ContentRegistry.Kind.RELIC))
                            .then(manualSelection("curse", ContentRegistry.Kind.CURSE)))
                    .then(manualSelection("ability", ContentRegistry.Kind.ABILITY))
                    .then(manualSelection("relic", ContentRegistry.Kind.RELIC))
                    .then(manualSelection("curse", ContentRegistry.Kind.CURSE))
                    .then(CommandManager.literal("ally")
                            .then(CommandManager.argument("target",
                                    net.minecraft.command.argument.EntityArgumentType.player())
                                    .executes(context -> {
                                        ServerPlayerEntity self = context.getSource().getPlayer();
                                        ServerPlayerEntity target =
                                                net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "target");
                                        if (self == null || self == target) {
                                            return 0;
                                        }
                                        com.kingdomunique.war.Warfare.setAlly(self, target);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("war")
                            .then(CommandManager.argument("target",
                                    net.minecraft.command.argument.EntityArgumentType.player())
                                    .executes(context -> {
                                        ServerPlayerEntity self = context.getSource().getPlayer();
                                        ServerPlayerEntity target =
                                                net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "target");
                                        if (self == null || self == target) {
                                            return 0;
                                        }
                                        com.kingdomunique.war.Warfare.declareWar(self, target);
                                        return 1;
                                    })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> manualSelection(String name, ContentRegistry.Kind kind) {
        return CommandManager.literal(name)
                .requires(source -> source.hasPermissionLevel(SelectionManager.MANUAL_SELECTION_PERMISSION_LEVEL))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    return player != null && SelectionManager.reopenOrOpen(player, kind) ? 1 : 0;
                });
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SelectionEventHandler::onTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GLUTTONY_UNTIL.remove(handler.getPlayer().getUuid());
        });

        ServerPlayNetworking.registerGlobalReceiver(SelectionChoicePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            SelectionManager.handleChoice(player, payload.kind(), payload.chosenId());
        });
        // 手動選択の権限はコマンドと同じ。
        ServerPlayNetworking.registerGlobalReceiver(
                com.kingdomunique.network.RequestSelectionPayload.ID, (payload, context) ->
                        SelectionManager.reopenOrOpen(context.player(), payload.kind()));

        ServerLivingEntityEvents.AFTER_DEATH.register(SelectionEventHandler::onDeath);
        ServerLivingEntityEvents.ALLOW_DEATH.register(SelectionEventHandler::allowDeath);
    }

    private static void onTick(MinecraftServer server) {
        RelicEffects.onServerTick(server);
        CurseEffects.onServerTick(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            checkDiamond(player);
            checkNether(player);
            checkCitizens(player);
            tickGluttony(player);
            blockHostileSpawns(player);
        }
    }

    /** ダイヤモンド（原石／インゴット）を初めて所持したらトリガーA。 */
    private static void checkDiamond(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(net.minecraft.item.Items.DIAMOND)) {
                SelectionManager.onDiamondObtained(player);
                return;
            }
        }
    }

    private static void checkNether(ServerPlayerEntity player) {
        if (player.getWorld().getRegistryKey() == World.NETHER) {
            SelectionManager.onNetherEntered(player);
        }
    }

    private static void checkCitizens(ServerPlayerEntity player) {
        // 国民（王国データの人口）だけを数える。周辺の未所属村人は含めない。
        int count = com.kingdomunique.kingdom.KingdomManager.getOrCreate(player).population();
        SelectionManager.onCitizenCountReached(player, count);
    }

    /** 暴食の指輪：空腹の減少を3倍にし、空腹0で猛毒。 */
    private static void tickGluttony(ServerPlayerEntity player) {
        if (!CurseEffects.has(player, CurseRegistry.GLUTTONY)) {
            return;
        }
        long now = player.getServerWorld().getTime();
        Long until = GLUTTONY_UNTIL.get(player.getUuid());
        boolean buffActive = until != null && now < until;
        if (buffActive) {
            if (player.getHungerManager().getFoodLevel() == 0 && now % 20L == 0L) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 1));
            }
            player.addExhaustion(0.06F);
        } else {
            player.addExhaustion(0.02F);
        }
    }

    /**
     * 守護の結界石：自国領土内に自然湧きした敵対Mobを除去する。
     * 襲撃軍・名前付き・味方化Mobなどの永続Mobとボスは対象外（襲撃の無効化や味方の消滅を防ぐ）。
     */
    private static void blockHostileSpawns(ServerPlayerEntity player) {
        if (player.getServer().getTicks() % 20 != 0 || !RelicEffects.isHostileSpawnBlocked(player)) {
            return;
        }
        ServerWorld world = player.getServer().getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        UUID owner = player.getUuid();
        net.minecraft.util.math.BlockPos center = com.kingdomunique.kingdom.KingdomManager.centerOf(world, owner);
        if (center == null) {
            return;
        }
        int radius = com.kingdomunique.kingdom.KingdomManager.territoryRadius(world, owner);
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                center.getX() - radius, world.getBottomY(), center.getZ() - radius,
                center.getX() + radius, world.getTopY(), center.getZ() + radius);
        for (HostileEntity hostile : world.getEntitiesByClass(HostileEntity.class, box,
                h -> h.isAlive() && !h.isPersistent()
                        && !com.kingdomunique.ability.AlliedMobManager.isAlly(h)
                        && com.kingdomunique.ability.AbilityActions.canLift(h)
                        && com.kingdomunique.kingdom.KingdomManager.isInTerritory(world, owner, h.getPos()))) {
            hostile.discard();
        }
    }

    /** 撃破時の処理：宝剣の攻撃力上昇、暴食のバフ、背徳の呪いダメージ。 */
    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        // 殺戮の宝剣の攻撃力上昇は FinalEventHandler.onDeath で一元処理する（二重加算を避ける）。

        if (CurseEffects.has(player, CurseRegistry.GLUTTONY)) {
            GLUTTONY_UNTIL.put(player.getUuid(),
                    player.getServerWorld().getTime() + GLUTTONY_TICKS);
        }

        // 背徳の契約書の呪いは KingdomEventHandler.onDeath（国民の死亡時）で一元処理する。
    }

    /** 不死鳥の羽根：1日1回、死亡をキャンセルして全回復。 */
    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }
        if (!RelicEffects.has(player, RelicRegistry.PHOENIX)) {
            return true;
        }
        long day = player.getServerWorld().getTimeOfDay() / DAY;
        ServerWorld overworld = player.getServer().getWorld(World.OVERWORLD);
        if (overworld == null || !WorldUniqueData.getOrCreate(overworld).claimPhoenixRevive(player.getUuid(), day)) {
            return true;
        }
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.sendMessage(Text.literal("§6[不死鳥の羽根] あなたは灰の中から蘇りました。"), false);
        return false;
    }
}
