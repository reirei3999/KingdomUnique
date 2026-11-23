package com.kingdomunique.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.UUID;

/** 王が1王国につき1回（軍旗で2回）召喚できる英雄NPC。 */
public final class HeroEntity extends PathAwareEntity {

    private HeroClass heroClass = HeroClass.KNIGHT;
    private HeroMode mode = HeroMode.FOLLOW;
    private UUID ownerUuid = null;
    private long lastSpecialTick = 0L;

    public HeroEntity(EntityType<HeroEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createHeroAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25D)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0D)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.add(2, new HeroFollowGoal());
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, HostileEntity.class, true,
                target -> !com.kingdomunique.ability.AlliedMobManager.isAlly(target)));
    }

    public HeroClass getHeroClass() {
        return heroClass;
    }

    public void setHeroClass(HeroClass heroClass) {
        this.heroClass = heroClass;
        applyClassStats();
    }

    public HeroMode getMode() {
        return mode;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    /** 召喚直後にクラス性能を属性へ反映する。 */
    public void applyClassStats() {
        HeroClass stats = heroClass;
        this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(stats.getHealth());
        this.setHealth(stats.getHealth());
        this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(stats.getAttackDamage());
        this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(stats.getSpeed());
        this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(stats.getArmor());
        this.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE).setBaseValue(stats.getFollowRange());
        if (stats == HeroClass.ASSASSIN) {
            this.getAttributeInstance(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER).setBaseValue(0.0D);
        }
        applyDefaultEquipment();
    }

    /** クラス別の見た目用防具・武器（バニラアイテム）。 */
    private void applyDefaultEquipment() {
        net.minecraft.item.ItemStack weapon = switch (heroClass) {
            case ASSASSIN -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD);
            case KNIGHT -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD);
            case ARCHER -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.BOW);
            case SMITH -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_PICKAXE);
        };
        this.equipStack(EquipmentSlot.MAINHAND, weapon);
        boolean heavy = heroClass == HeroClass.KNIGHT;
        net.minecraft.item.Item helmet = heavy ? net.minecraft.item.Items.IRON_HELMET : net.minecraft.item.Items.LEATHER_HELMET;
        net.minecraft.item.Item chest = heavy ? net.minecraft.item.Items.IRON_CHESTPLATE : net.minecraft.item.Items.LEATHER_CHESTPLATE;
        net.minecraft.item.Item legs = heavy ? net.minecraft.item.Items.IRON_LEGGINGS : net.minecraft.item.Items.LEATHER_LEGGINGS;
        net.minecraft.item.Item boots = heavy ? net.minecraft.item.Items.IRON_BOOTS : net.minecraft.item.Items.LEATHER_BOOTS;
        this.equipStack(EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(helmet));
        this.equipStack(EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(chest));
        this.equipStack(EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(legs));
        this.equipStack(EquipmentSlot.FEET, new net.minecraft.item.ItemStack(boots));
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("ku_hero_class", heroClass.name());
        nbt.putString("ku_hero_mode", mode.name());
        nbt.putString("ku_hero_owner", ownerUuid == null ? "" : ownerUuid.toString());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        try {
            heroClass = HeroClass.valueOf(nbt.getString("ku_hero_class"));
        } catch (IllegalArgumentException ignored) {
            heroClass = HeroClass.KNIGHT;
        }
        try {
            mode = HeroMode.valueOf(nbt.getString("ku_hero_mode"));
        } catch (IllegalArgumentException ignored) {
            mode = HeroMode.FOLLOW;
        }
        String owner = nbt.getString("ku_hero_owner");
        ownerUuid = owner.isEmpty() ? null : UUID.fromString(owner);
    }

    /** 右クリックでモード切替（孤高の王冠所持時は随伴不可）。 */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !serverPlayer.getUuid().equals(ownerUuid)) {
            return super.interactMob(player, hand);
        }
        boolean allowFollow = !com.kingdomunique.effect.CurseEffects.has(serverPlayer,
                com.kingdomunique.registry.CurseRegistry.SOLITUDE);
        mode = mode.next(allowFollow);
        serverPlayer.sendMessage(Text.literal("§6[英雄] モード: §f" + mode.getDisplayName()), true);
        return ActionResult.SUCCESS;
    }

    /** 騎士の範囲斬撃・鍛冶師の装備修復を定期実行する。 */
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        long now = this.getWorld().getTime();
        if (now - lastSpecialTick < 40L) {
            return;
        }
        lastSpecialTick = now;

        // Gemini 修正: 戦時中は敵国の兵士・英雄・王を自動索敵して攻撃対象にロックする。
        LivingEntity warTarget = com.kingdomunique.war.KingdomWarTargetHelper.findBestTarget(
                (net.minecraft.server.world.ServerWorld) this.getWorld(), this, 24.0D);
        if (warTarget != null) {
            this.setTarget(warTarget);
        }

        if (heroClass == HeroClass.KNIGHT) {
            for (HostileEntity hostile : this.getWorld().getEntitiesByClass(HostileEntity.class,
                    this.getBoundingBox().expand(3.5D),
                    h -> h.isAlive() && !com.kingdomunique.ability.AlliedMobManager.isAlly(h))) {
                hostile.damage(this.getDamageSources().mobAttack(this), 6.0F);
            }
            com.kingdomunique.war.Warfare.taunt(this);
        } else if (heroClass == HeroClass.SMITH) {
            ServerPlayerEntity owner = owner();
            if (owner == null) {
                return;
            }
            repair(owner.getMainHandStack());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                repair(owner.getEquippedStack(slot));
            }
            for (ItemStack stack : this.getArmorItems()) {
                repair(stack);
            }
        }
    }

    private void repair(ItemStack stack) {
        if (!stack.isEmpty() && stack.isDamaged() && stack.isDamageable()) {
            stack.setDamage(stack.getDamage() - 1);
        }
    }

    private ServerPlayerEntity owner() {
        if (ownerUuid == null) {
            return null;
        }
        if (!(((net.minecraft.server.world.ServerWorld) this.getWorld()).getEntity(ownerUuid)
                instanceof ServerPlayerEntity player)) {
            return null;
        }
        return player;
    }

    /** 追従Goal（FOLLOW時のみ有効）。 */
    private final class HeroFollowGoal extends Goal {
        private int counter = 0;

        @Override
        public boolean canStart() {
            return mode == HeroMode.FOLLOW && owner() != null;
        }

        @Override
        public void tick() {
            if (++counter < 10) {
                return;
            }
            counter = 0;
            ServerPlayerEntity owner = owner();
            if (owner != null && HeroEntity.this.squaredDistanceTo(owner) > 9.0D) {
                HeroEntity.this.getNavigation().startMovingTo(owner, 1.1D);
            }
        }
    }
}
