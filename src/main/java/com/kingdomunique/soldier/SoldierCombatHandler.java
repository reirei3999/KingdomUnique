package com.kingdomunique.soldier;

import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.util.EnhancementUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** 兵士が装備した武器（強化段階・遺物効果込み）で戦闘するAI。 */
public final class SoldierCombatHandler {

    private SoldierCombatHandler() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SoldierCombatHandler::onWorldTick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, baseDamageTaken, damageTaken, blocked) -> {
            if (target.getWorld().isClient()) {
                return;
            }
            Entity attacker = source.getAttacker();
            if (attacker instanceof VillagerEntity soldier
                    && soldier instanceof CitizenVillager citizen
                    && CitizenVillager.ROLE_SOLDIER.equals(citizen.ku$getRole())) {
                applyUniqueWeaponEffects(soldier, target, damageTaken);
            }
        });
    }

    private static void onWorldTick(ServerWorld world) {
        // Gemini 修正: 0.5秒ごとに高頻度判定し、逃走AIを解除して確実に戦闘させる。
        if (world.getTime() % 10L != 0L) {
            return;
        }
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof VillagerEntity soldier)
                    || !(soldier instanceof CitizenVillager citizen)
                    || !CitizenVillager.ROLE_SOLDIER.equals(citizen.ku$getRole())
                    || !soldier.isAlive()) {
                continue;
            }
            // 武器が消えないよう自動拾得を禁止する。
            soldier.setCanPickUpLoot(false);

            // Gemini 修正: 視界14マス以内で視線が通っている敵だけを索敵。
            LivingEntity target = SoldierOrderManager.findVisibleEnemyInSight(world, soldier, 14.0D);
            if (target == null) {
                soldier.setTarget(null);
                // 敵が視界にいない場合は移動命令・王追従・防衛を実行。
                SoldierOrderManager.tickMovementOrder(world, soldier);
                continue;
            }
            // バニラ村人の逃走状態を解除して戦闘ターゲットへロック。
            soldier.setTarget(target);
            soldier.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.AVOID_TARGET);
            soldier.getLookControl().lookAt(target, 30.0f, 30.0f);

            ItemStack weapon = soldier.getMainHandStack();
            boolean melee = !(weapon.getItem() instanceof RangedWeaponItem)
                    && !(weapon.getItem() instanceof TridentItem);
            // 攻撃・射撃は1秒ごと（20tick）に実行。
            boolean attackTick = world.getTime() % 20L == 0L;

            if (!melee) {
                if (attackTick) {
                    performRangedAttack(world, soldier, target, weapon);
                }
                continue;
            }
            double distSq = soldier.squaredDistanceTo(target);
            if (distSq <= 10.24D) {
                if (attackTick) {
                    soldier.swingHand(Hand.MAIN_HAND);
                    target.damage(world.getDamageSources().mobAttack(soldier), calculateWeaponDamage(weapon));
                }
            } else {
                soldier.getNavigation().startMovingTo(target, 1.05D);
            }
        }
    }

    /** 強化段階（1.1^N）が反映された AttributeModifiers から攻撃力を算出する。 */
    private static float calculateWeaponDamage(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return 2.0f;
        }
        double damage = 1.0D;
        AttributeModifiersComponent modifiers = weapon.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
                    damage += entry.modifier().value();
                }
            }
        }
        return (float) Math.max(2.0D, damage * EnhancementUtil.multiplier(EnhancementUtil.getLevel(weapon)));
    }

    /** 弓・クロスボウ・トライデントの射撃（強化倍率を威力へ反映）。 */
    private static void performRangedAttack(ServerWorld world, VillagerEntity soldier,
                                            LivingEntity target, ItemStack weapon) {
        soldier.swingHand(Hand.MAIN_HAND);
        float power = Math.max(1.0f, calculateWeaponDamage(weapon) / 6.0f);
        Vec3d dir = target.getEyePos().subtract(soldier.getEyePos()).normalize();

        PersistentProjectileEntity projectile;
        if (weapon.getItem() instanceof TridentItem) {
            net.minecraft.entity.projectile.TridentEntity trident =
                    new net.minecraft.entity.projectile.TridentEntity(world, soldier, weapon.copy());
            trident.setVelocity(dir.x, dir.y + 0.08D, dir.z, 1.8f, 2.0f);
            trident.setDamage(trident.getDamage() * power);
            projectile = trident;
        } else {
            projectile = ProjectileUtil.createArrowProjectile(soldier,
                    new net.minecraft.item.ItemStack(net.minecraft.item.Items.ARROW), 1.0f, weapon);
            projectile.setVelocity(dir.x, dir.y + 0.1D, dir.z, 1.9f, 2.0f);
            projectile.setDamage(projectile.getDamage() * power);
        }
        world.spawnEntity(projectile);
    }

    /** 遺物「殺戮の宝剣」を装備した兵士の特殊性（吸血＋範囲斬撃）。 */
    private static void applyUniqueWeaponEffects(VillagerEntity soldier, LivingEntity target, float damageDealt) {
        ItemStack weapon = soldier.getMainHandStack();
        if (weapon.isEmpty()) {
            return;
        }
        boolean slaughter = weapon.isOf(com.kingdomunique.item.ModItems.SLAUGHTER_BLADE)
                || weapon.getName().getString().contains("殺戮の宝剣");
        if (!slaughter) {
            return;
        }
        soldier.heal(Math.max(1.0f, damageDealt * 0.25f));
        ServerWorld world = (ServerWorld) soldier.getWorld();
        for (HostileEntity extra : world.getEntitiesByClass(HostileEntity.class,
                new Box(target.getBlockPos()).expand(3.0D), e -> e != target && e.isAlive())) {
            extra.damage(world.getDamageSources().mobAttack(soldier), damageDealt * 0.5f);
        }
    }

    private static LivingEntity findNearestEnemy(ServerWorld world, VillagerEntity soldier, double radius) {
        Box searchBox = new Box(soldier.getBlockPos()).expand(radius);
        List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class,
                searchBox, LivingEntity::isAlive);
        LivingEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (HostileEntity hostile : hostiles) {
            double dist = soldier.squaredDistanceTo(hostile);
            if (dist < minDist) {
                minDist = dist;
                closest = hostile;
            }
        }
        return closest;
    }
}
