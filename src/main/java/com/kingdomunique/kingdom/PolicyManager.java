package com.kingdomunique.kingdom;

import com.kingdomunique.war.DiplomacyData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 王国政策の変更・効果・コマンドを管理する。 */
public final class PolicyManager {

    /** 7日 = 7 * 24000 = 168000 ティック。 */
    public static final long POLICY_CYCLE_TICKS = 168000L;

    private PolicyManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PolicyManager::onTick);

        // 王城のビーコンを右クリックすると政策選択を提示する（バニラGUIは開かせない）。
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity king)
                    || !world.getBlockState(hit.getBlockPos()).isOf(Blocks.BEACON)) {
                return ActionResult.PASS;
            }
            KingdomData.KingdomInfo info = KingdomData.getOrCreate((ServerWorld) world).get(king.getUuid());
            if (info == null || hit.getBlockPos().getX() != info.centerX()
                    || hit.getBlockPos().getZ() != info.centerZ()) {
                return ActionResult.PASS;
            }
            sendPolicyOffer(king);
            return ActionResult.SUCCESS;
        });
    }

    public static void registerCommands(LiteralArgumentBuilder<ServerCommandSource> ku) {
        ku.then(CommandManager.literal("policy")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    sendPolicyOffer(player);
                    return 1;
                })
                .then(CommandManager.argument("choice", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) {
                                return 0;
                            }
                            Policy policy = Policy.fromString(
                                    StringArgumentType.getString(context, "choice"));
                            if (policy == null) {
                                player.sendMessage(Text.literal(
                                        "§c[政策] 選択肢は rich / industry / border のいずれかです。"), false);
                                return 0;
                            }
                            return setPolicy(player, policy) ? 1 : 0;
                        })));
    }

    /** 現在の政策を返す（未設定なら null）。 */
    public static Policy getPolicy(ServerWorld world, UUID owner) {
        PolicyData.PolicyState state = PolicyData.getOrCreate(world).get(owner);
        if (state == null || state.policy() == null) {
            return null;
        }
        return Policy.fromString(state.policy());
    }

    /** 政策を変更する。同じ週（168000ティック）内なら1回のみ。 */
    public static boolean setPolicy(ServerPlayerEntity player, Policy policy) {
        ServerWorld world = player.getServerWorld();
        PolicyData data = PolicyData.getOrCreate(world);
        PolicyData.PolicyState state = data.getOrCreate(player.getUuid());
        int cycle = (int) (world.getTimeOfDay() / POLICY_CYCLE_TICKS);

        if (state.lastCycle() == cycle && state.policy() != null) {
            player.sendMessage(Text.literal("§c[政策] 今週の方針はすでに決定済みです。次の7日後に変更できます。"), false);
            return false;
        }
        data.put(player.getUuid(), state.withPolicy(policy.name(), cycle));
        player.getServer().getPlayerManager().broadcast(
                Text.literal("§6§l【王国の政策】§r " + player.getName().getString()
                        + " が「" + policy.getDisplayName() + "」を布いた。"), false);
        return true;
    }

    /** 王城クリック・コマンド時に政策選択肢をクリック可能な形で提示する。 */
    public static void sendPolicyOffer(ServerPlayerEntity player) {
        Policy current = getPolicy(player.getServerWorld(), player.getUuid());
        player.sendMessage(Text.literal("§6====== 王国の政策（7日ごとに1回変更可）======"), false);
        player.sendMessage(Text.literal("現在: " + (current == null ? "未設定" : current.getDisplayName())), false);
        for (Policy policy : Policy.values()) {
            Text line = Text.literal("§e[" + policy.getDisplayName() + "] §f" + policy.getDescription())
                    .styled(style -> style.withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND, "/ku policy " + policy.commandName())));
            player.sendMessage(line, false);
        }
    }

    /** 兵士NPCの攻撃力倍率（軍旗と政策を合成）。 */
    public static double soldierAttackMultiplier(ServerWorld world, UUID owner) {
        double value = com.kingdomunique.effect.RelicEffects.getSoldierAttackMultiplierOwner(world, owner);
        Policy policy = getPolicy(world, owner);
        if (policy == Policy.RICH_STRONG) {
            value *= 1.2D;
        } else if (policy == Policy.INDUSTRY) {
            value *= 0.8D;
        }
        return value;
    }

    /** 建築時間倍率（殖産興業令で30%短縮）。 */
    public static double buildTimeMultiplier(ServerWorld world, UUID owner) {
        return getPolicy(world, owner) == Policy.INDUSTRY ? 0.7D : 1.0D;
    }

    /** 作物成長倍率（富国強兵令0.8 / 殖産興業令1.3）。 */
    public static double cropGrowthMultiplier(ServerWorld world, UUID owner) {
        Policy policy = getPolicy(world, owner);
        if (policy == Policy.INDUSTRY) {
            return 1.3D;
        }
        if (policy == Policy.RICH_STRONG) {
            return 0.8D;
        }
        return 1.0D;
    }

    /** 国境監視塔の感知範囲倍率（国境警備令で1.5倍）。 */
    public static double watchtowerRangeMultiplier(ServerWorld world, UUID owner) {
        return getPolicy(world, owner) == Policy.BORDER_GUARD ? 1.5D : 1.0D;
    }

    private static long cropTick = 0L;

    private static void onTick(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        cropTick++;
        if (cropTick % 20L != 0L) {
            return;
        }
        DiplomacyData diplomacy = DiplomacyData.getOrCreate(world);
        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            Policy policy = getPolicy(world, owner.getUuid());
            if (policy == Policy.BORDER_GUARD) {
                applyBorderGuard(world, owner, diplomacy);
            }
            if (policy == Policy.INDUSTRY || policy == Policy.RICH_STRONG) {
                tickPolicyCrops(world, owner, cropGrowthMultiplier(world, owner.getUuid()));
            }
        }
    }

    /** 国境警備令: 領土内の敵プレイヤーへ移動速度低下Iを付与。 */
    private static void applyBorderGuard(ServerWorld world, ServerPlayerEntity owner,
                                         DiplomacyData diplomacy) {
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (other.getUuid().equals(owner.getUuid())
                    || diplomacy.isAllied(owner.getUuid(), other.getUuid())) {
                continue;
            }
            if (KingdomManager.isInTerritory(world, owner.getUuid(), other.getPos())) {
                other.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, 40, 0, true, true));
            }
        }
    }

    /** 政策による作物成長倍率を王の周囲へ反映する。 */
    private static void tickPolicyCrops(ServerWorld world, ServerPlayerEntity owner, double multiplier) {
        BlockPos center = owner.getBlockPos();
        int radius = 16;
        double chance = Math.min(1.0D, 0.15D * multiplier);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius
                        || ThreadLocalRandom.current().nextDouble() > chance) {
                    continue;
                }
                BlockPos pos = center.add(dx, 0, dz);
                BlockState state = world.getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                    crop.applyGrowth(world, pos, state);
                }
            }
        }
    }
}
