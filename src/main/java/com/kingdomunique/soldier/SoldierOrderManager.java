package com.kingdomunique.soldier;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.network.SoldierOrderPayload;
import com.kingdomunique.war.KingdomWarTargetHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 兵士への命令（移動・行動方針）と視界付き索敵（Gemini 提示を既存方式へ接続）。 */
public final class SoldierOrderManager {

    public enum OrderStance {
        FREE("自由行動"), FOLLOW("王に追従"), HOLD("指定地点防衛");

        public final String label;

        OrderStance(String label) {
            this.label = label;
        }
    }

    private static final double ORDER_MOVEMENT_SPEED = 0.75D;
    private static final Map<UUID, BlockPos> MOVE_ORDERS = new HashMap<>();
    private static final Map<UUID, OrderStance> STANCES = new HashMap<>();

    private SoldierOrderManager() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SoldierOrderPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> handleOrder(player, payload));
        });
    }

    static void handleOrder(ServerPlayerEntity king, SoldierOrderPayload payload) {
        ServerWorld world = king.getServerWorld();
        List<VillagerEntity> targets = new ArrayList<>();

        if (payload.isMassOrder()) {
            Box area = new Box(king.getBlockPos()).expand(32.0D);
            targets = world.getEntitiesByClass(VillagerEntity.class, area,
                    v -> v.isAlive() && isSoldier(v)
                            && SoldierInteractionHandler.isOwnedByKing((CitizenVillager) v, king.getUuid()));
        } else {
            Entity e = world.getEntityById(payload.targetEntityId());
            if (e instanceof VillagerEntity v && v.isAlive() && isSoldier(v)
                    && SoldierInteractionHandler.isOwnedByKing((CitizenVillager) v, king.getUuid())) {
                targets.add(v);
            }
        }
        if (targets.isEmpty()) {
            king.sendMessage(Text.literal("§c命令可能な自国兵士が範囲内にいません。"), true);
            return;
        }

        String scope = payload.isMassOrder() ? "一斉命令(" + targets.size() + "名)" : "個別命令(1名)";
        if (payload.actionType() == 0) {
            BlockPos dest = payload.moveTargetPos();
            for (VillagerEntity soldier : targets) {
                MOVE_ORDERS.put(soldier.getUuid(), dest);
                STANCES.put(soldier.getUuid(), OrderStance.HOLD);
                soldier.getNavigation().startMovingTo(
                        dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D, ORDER_MOVEMENT_SPEED);
            }
            world.spawnParticles(ParticleTypes.END_ROD, dest.getX() + 0.5D, dest.getY() + 1.2D,
                    dest.getZ() + 0.5D, 20, 0.3D, 0.8D, 0.3D, 0.02D);
            world.playSound(null, king.getBlockPos(), SoundEvents.ITEM_GOAT_HORN_PLAY,
                    SoundCategory.PLAYERS, 0.6f, 1.4f);
            king.sendMessage(Text.literal("§b[" + scope + "] §f指定地点 (" + dest.getX() + ", "
                    + dest.getY() + ", " + dest.getZ() + ") へ進軍命令を出しました！"), true);
        } else {
            OrderStance next = OrderStance.FOLLOW;
            for (VillagerEntity soldier : targets) {
                OrderStance current = STANCES.getOrDefault(soldier.getUuid(), OrderStance.FREE);
                next = switch (current) {
                    case FREE -> OrderStance.FOLLOW;
                    case FOLLOW -> OrderStance.HOLD;
                    case HOLD -> OrderStance.FREE;
                };
                STANCES.put(soldier.getUuid(), next);
                if (next == OrderStance.HOLD) {
                    MOVE_ORDERS.put(soldier.getUuid(), soldier.getBlockPos());
                } else {
                    MOVE_ORDERS.remove(soldier.getUuid());
                }
            }
            king.sendMessage(Text.literal("§e[" + scope + "] §f行動方針を『" + next.label + "』に切り替えました。"), true);
        }
    }

    private static boolean isSoldier(VillagerEntity v) {
        return v instanceof CitizenVillager c && CitizenVillager.ROLE_SOLDIER.equals(c.ku$getRole());
    }

    /** 視界（14マス・canSee、至近5マスは気配）制限付きの敵索敵。 */
    public static LivingEntity findVisibleEnemyInSight(ServerWorld world, VillagerEntity soldier,
                                                       double maxSightRange) {
        return KingdomWarTargetHelper.findBestTarget(world, soldier, maxSightRange, candidate -> {
            double distSq = soldier.squaredDistanceTo(candidate);
            return distSq <= maxSightRange * maxSightRange && (distSq <= 25.0D || soldier.canSee(candidate));
        });
    }

    public static void waitAtHoldPosition(VillagerEntity soldier) {
        if (soldier.getTarget() != null || STANCES.get(soldier.getUuid()) != OrderStance.HOLD) {
            return;
        }
        BlockPos hold = MOVE_ORDERS.get(soldier.getUuid());
        if (hold != null && isAtHoldPosition(soldier, hold)) {
            soldier.getBrain().forget(MemoryModuleType.WALK_TARGET);
            soldier.getNavigation().stop();
        }
    }

    private static boolean isAtHoldPosition(VillagerEntity soldier, BlockPos hold) {
        return soldier.getBlockPos().getSquaredDistance(hold) <= 6.25D;
    }

    /** 敵が見えていないときの移動命令・王追従・防衛の移動制御。 */
    public static void tickMovementOrder(ServerWorld world, VillagerEntity soldier) {
        UUID id = soldier.getUuid();
        OrderStance stance = STANCES.getOrDefault(id, OrderStance.FREE);
        if (stance == OrderStance.HOLD) {
            BlockPos hold = MOVE_ORDERS.get(id);
            if (hold != null && !isAtHoldPosition(soldier, hold)) {
                soldier.getNavigation().startMovingTo(hold.getX() + 0.5D, hold.getY(),
                        hold.getZ() + 0.5D, ORDER_MOVEMENT_SPEED);
            }
        } else if (stance == OrderStance.FOLLOW) {
            UUID owner = KingdomWarTargetHelper.getOwnerUuid(soldier);
            if (owner != null) {
                ServerPlayerEntity king = world.getServer().getPlayerManager().getPlayer(owner);
                if (king != null && king.getServerWorld() == world) {
                    double distSq = soldier.squaredDistanceTo(king);
                    if (distSq > 16.0D && distSq < 2500.0D) {
                        soldier.getNavigation().startMovingTo(king, ORDER_MOVEMENT_SPEED);
                    }
                }
            }
        }
    }
}
