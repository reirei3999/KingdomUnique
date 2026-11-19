package com.kingdomunique.item.unique;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.WorldUniqueData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 一点モノ武器の排他 transfer・村チェスト配置・固有ギミック。 */
public final class UniqueWeaponManager {

    /** チェストに置かれている間の所有UUID。 */
    public static final UUID WORLD_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final Map<UUID, Long> GAE_BOLG_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> GAE_THROW_TIME = new HashMap<>();
    private static final Map<UUID, Long> FROZEN_UNTIL = new HashMap<>();
    private static final java.util.Set<String> ANNOUNCED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long GAE_FULL = 36_000L;
    private static final long GAE_HALF = 18_000L;

    private UniqueWeaponManager() {
    }

    /** プレイヤーインベントリを走査し、チェスト所有の武器をプレイヤーへ移す。 */
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            WorldUniqueData data = WorldUniqueData.getOrCreate(
                    player.getServer().getWorld(World.OVERWORLD));
            for (String id : UniqueWeapons.ALL_IDS) {
                if (!contains(player, id)) {
                    continue;
                }
                UUID owner = data.getOwner(id);
                boolean transferred;
                if (WORLD_OWNER.equals(owner)) {
                    transferred = data.transferId(WORLD_OWNER, player.getUuid(), id);
                } else if (owner == null) {
                    // 旧データやチェスト配置前に拾った場合: 所有情報を修復する。
                    transferred = data.claimId(player.getUuid(), id);
                } else {
                    transferred = false;
                }
                if (transferred && ANNOUNCED.add(player.getUuid() + "|" + id)) {
                    player.sendMessage(Text.literal("§6[ユニーク武器] " + id + " を手に入れた！"), false);
                }
            }
        }
    }

    /** 時間停止（0.5秒）。 */
    public static void freeze(LivingEntity target) {
        if (target instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.setAiDisabled(true);
        }
        target.setNoGravity(true);
        target.setVelocity(0.0D, 0.0D, 0.0D);
        FROZEN_UNTIL.put(target.getUuid(), System.currentTimeMillis() + 500L);
    }

    /** エクスカリバーのオーラと時間停止の解除を毎Tick処理する。 */
    public static void tickEffects(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (held(player, "excalibur")
                    && com.kingdomunique.kingdom.KingdomManager.isInTerritory(player)
                    && player.getServerWorld().getTime() % 20L == 0L) {
                for (net.minecraft.entity.passive.VillagerEntity villager
                        : player.getServerWorld().getEntitiesByClass(
                        net.minecraft.entity.passive.VillagerEntity.class,
                        player.getBoundingBox().expand(15.0D), v -> v.isAlive())) {
                    if (villager instanceof com.kingdomunique.entity.CitizenVillager citizen
                            && player.getUuid().toString().equals(citizen.ku$getOwnerUuid())) {
                        villager.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1));
                        villager.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0));
                    }
                }
            }
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(FROZEN_UNTIL).entrySet()) {
            if (now >= entry.getValue()) {
                FROZEN_UNTIL.remove(entry.getKey());
                for (ServerWorld world : server.getWorlds()) {
                    if (world.getEntity(entry.getKey()) instanceof LivingEntity entity) {
                        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                            mob.setAiDisabled(false);
                        }
                        entity.setNoGravity(false);
                    }
                }
            }
        }
    }

    private static boolean contains(ServerPlayerEntity player, String id) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (id.equals(stack.get(ModDataComponents.UNIQUE_WEAPON_ID))) {
                return true;
            }
        }
        return false;
    }

    /** ステップ1の10村のチェストへ未取得の武器を1本ずつ配置する。 */
    public static void distributeToVillages(ServerWorld world) {
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);
        int index = 0;
        for (com.kingdomunique.data.WorldUniqueData.VillageSite site : data.getVillageSites()) {
            if (index >= UniqueWeapons.ALL_IDS.length) {
                break;
            }
            String id = UniqueWeapons.ALL_IDS[index++];
            if (data.isClaimed(id)) {
                continue;
            }
            BlockPos base = new BlockPos(site.x(),
                    world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, site.x(), site.z()) + 1,
                    site.z());
            BlockPos chestPos = base.add(2, 0, 2);
            net.minecraft.inventory.Inventory inventory = null;
            if (world.getBlockEntity(chestPos) instanceof net.minecraft.inventory.Inventory found) {
                inventory = found;
            } else {
                world.setBlockState(chestPos, net.minecraft.block.Blocks.CHEST.getDefaultState());
                if (world.getBlockEntity(chestPos) instanceof net.minecraft.inventory.Inventory created) {
                    inventory = created;
                }
            }
            if (inventory == null) {
                continue;
            }
            inventory.setStack(0, new ItemStack(uniqueStack(id).getItem()));
            data.claimId(WORLD_OWNER, id);
        }
    }

    private static ItemStack uniqueStack(String id) {
        return switch (id) {
            case "excalibur" -> new ItemStack(UniqueWeapons.EXCALIBUR);
            case "gae_bolg" -> new ItemStack(UniqueWeapons.GAE_BOLG);
            case "muramasa" -> new ItemStack(UniqueWeapons.MURAMASA);
            case "apollo_bow" -> new ItemStack(UniqueWeapons.APOLLO_BOW);
            case "mjolnir" -> new ItemStack(UniqueWeapons.MJOLNIR);
            default -> new ItemStack(UniqueWeapons.CHRONOS_DAGGER);
        };
    }

    public static boolean held(ServerPlayerEntity player, String id) {
        return id.equals(player.getMainHandStack().get(ModDataComponents.UNIQUE_WEAPON_ID));
    }

    /** ゲイボルグ投擲（本体は減らさず、拾えない専用projectileを出す）。 */
    public static void throwGaeBolg(ServerPlayerEntity player) {
        GAE_THROW_TIME.put(player.getUuid(), System.currentTimeMillis());
        ServerWorld world = player.getServerWorld();
        GaeBolgEntity trident = new GaeBolgEntity(world, player, new ItemStack(UniqueWeapons.GAE_BOLG));
        Vec3d look = player.getRotationVec(1.0F);
        trident.setVelocity(look.x * 3.0D, look.y * 3.0D, look.z * 3.0D);
        trident.addCommandTag("ku_gae_bolg");
        world.spawnEntity(trident);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW.value(),
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /** 投擲後5秒以内のキルでアイテムクールタイムを18秒に短縮する。 */
    public static void onPlayerKill(ServerPlayerEntity player) {
        Long thrown = GAE_THROW_TIME.get(player.getUuid());
        if (thrown == null || System.currentTimeMillis() - thrown > 5000L) {
            return;
        }
        GAE_THROW_TIME.remove(player.getUuid());
        player.getItemCooldownManager().set(UniqueWeapons.GAE_BOLG, GaeBolgItem.KILL_COOLDOWN);
        player.sendMessage(Text.literal("§b[ゲイボルグ] キルによりクールタイムが半減した！"), true);
    }

    /** ミョルニル：視線先の敵へ固定雷ダメージ＋落雷演出。 */
    public static void fireLightning(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        LivingEntity target = com.kingdomunique.ability.AbilityActions.findLookTarget(player, 64.0D);
        Vec3d pos = target == null
                ? player.getEyePos().add(player.getRotationVec(1.0F).multiply(24.0D))
                : target.getPos();
        if (target != null) {
            target.damage(target.getDamageSources().lightningBolt(), 45.0F);
        }
        Entity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            world.spawnEntity(bolt);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 0.8F, 1.0F);
    }

    /** アポロンの光の矢（消費なし・アンデッド2倍）。 */
    public static void fireLightArrow(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ArrowEntity arrow = EntityType.ARROW.create(world);
        if (arrow == null) {
            return;
        }
        arrow.setOwner(player);
        Vec3d look = player.getRotationVec(1.0F);
        arrow.setVelocity(look.x * 3.0D, look.y * 3.0D, look.z * 3.0D, 1.0F, 0.0F);
        arrow.addCommandTag("ku_light_arrow");
        world.spawnEntity(arrow);
    }

    /** 光の矢の空中起爆。 */
    public static void detonateLightArrow(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        boolean done = false;
        for (Entity entity : world.getEntitiesByClass(ArrowEntity.class,
                player.getBoundingBox().expand(64.0D),
                a -> a.getCommandTags().contains("ku_light_arrow"))) {
            Vec3d pos = entity.getPos();
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                    new net.minecraft.util.math.Box(pos.x - 5.0D, pos.y - 5.0D, pos.z - 5.0D,
                            pos.x + 5.0D, pos.y + 5.0D, pos.z + 5.0D),
                    living -> living != player && living.isAlive())) {
                float damage = 15.0F;
                target.damage(target.getDamageSources().mobAttack(player), damage);
                target.setOnFireFor(5);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
            }
            world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                    pos.x, pos.y, pos.z, 40, 1.0, 1.0, 1.0, 0.2);
            entity.discard();
            done = true;
        }
        if (done) {
            player.sendMessage(Text.literal("§6[太陽の爆発] 光の矢が起爆した！"), true);
        }
    }
}
