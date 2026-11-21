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
import net.minecraft.entity.projectile.PersistentProjectileEntity;
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

    /** プレイヤーUUID -> 最後に放った光の矢の Entity UUID。 */
    private static final Map<UUID, UUID> LIGHT_ARROW_BY_OWNER = new java.util.concurrent.ConcurrentHashMap<>();

    /** 光の矢のクールタイム（8秒 = 160ティック）。 */
    public static final int APOLLO_COOLDOWN_TICKS = 160;

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
                // 旧版で村のチェストに配布された武器を拾った場合など、所有者未登録なら所持者を持ち主にする。
                boolean transferred = data.getOwner(id) == null && data.claimId(player.getUuid(), id);
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

    /**
     * ユニーク武器は村のチェストには置かない。旧バージョンで村チェストに置かれた武器は、
     * チェストのチャンクが読み込まれたときに取り除き、取得条件で手に入るよう所有を解放する。
     */
    public static void clearVillageChestWeapons(ServerWorld world) {
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);
        java.util.List<WorldUniqueData.VillageSite> sites = data.getVillageSites();
        for (int index = 0; index < Math.min(sites.size(), UniqueWeapons.ALL_IDS.length); index++) {
            String id = UniqueWeapons.ALL_IDS[index];
            if (!WORLD_OWNER.equals(data.getOwner(id))) {
                continue;
            }
            WorldUniqueData.VillageSite site = sites.get(index);
            if (!world.isChunkLoaded((site.x() + 2) >> 4, (site.z() + 2) >> 4)) {
                continue;
            }
            removeFromChestColumn(world, site, id);
            data.releaseId(WORLD_OWNER, id);
        }
    }

    /** 村チェストに残っている武器を取り除く（取得条件で付与したときの重複防止）。 */
    public static void removeFromVillageChests(ServerWorld world, String id) {
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);
        for (WorldUniqueData.VillageSite site : data.getVillageSites()) {
            removeFromChestColumn(world, site, id);
        }
    }

    private static void removeFromChestColumn(ServerWorld world, WorldUniqueData.VillageSite site, String id) {
        BlockPos.Mutable pos = new BlockPos.Mutable(site.x() + 2, 0, site.z() + 2);
        for (int y = world.getBottomY(); y < world.getTopY(); y++) {
            pos.setY(y);
            if (!(world.getBlockEntity(pos) instanceof net.minecraft.inventory.Inventory inventory)) {
                continue;
            }
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (id.equals(inventory.getStack(slot).get(ModDataComponents.UNIQUE_WEAPON_ID))) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                    inventory.markDirty();
                }
            }
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

    /** 遺物・呪物・ユニーク武器のIDから対応する実体アイテムを生成する（実体が無ければ空）。 */
    public static ItemStack createStackForId(String id) {
        if (id == null) {
            return ItemStack.EMPTY;
        }
        if (java.util.Arrays.asList(UniqueWeapons.ALL_IDS).contains(id)) {
            return uniqueStack(id);
        }
        if ("blade_of_slaughter".equals(id)) {
            return new ItemStack(com.kingdomunique.item.ModItems.SLAUGHTER_BLADE);
        }
        if ("pickaxe_of_destruction".equals(id)) {
            return new ItemStack(com.kingdomunique.item.ModItems.DESTRUCTION_PICKAXE);
        }
        return ItemStack.EMPTY;
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

    /** 左クリック発射。クールタイム中なら何もしない。 */
    public static void fireLightArrowWithCooldown(ServerPlayerEntity player) {
        if (!player.getMainHandStack().isOf(UniqueWeapons.APOLLO_BOW)
                || player.getItemCooldownManager().isCoolingDown(UniqueWeapons.APOLLO_BOW)) {
            return;
        }
        player.getItemCooldownManager().set(UniqueWeapons.APOLLO_BOW, APOLLO_COOLDOWN_TICKS);
        fireLightArrow(player);
    }

    /** 光の矢（拾えない・炎付き・高速・アンデッド特効は RangedDamageMixin で処理）。 */
    public static void fireLightArrow(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ArrowEntity arrow = EntityType.ARROW.create(world);
        if (arrow == null) {
            return;
        }
        arrow.setOwner(player);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        arrow.setDamage(8.0D);
        arrow.setCritical(true);
        arrow.setOnFireFor(10);

        // create(world) は原点(0,0,0)に生成されるため、目の前へ明示的に配置する。
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F);
        arrow.refreshPositionAndAngles(
                eye.x + look.x * 0.6D,
                eye.y + look.y * 0.6D - 0.1D,
                eye.z + look.z * 0.6D,
                player.getYaw(), player.getPitch());
        arrow.setVelocity(look.x * 3.0D, look.y * 3.0D, look.z * 3.0D, 1.0F, 0.0F);
        arrow.addCommandTag("ku_light_arrow");
        world.spawnEntity(arrow);
        LIGHT_ARROW_BY_OWNER.put(player.getUuid(), arrow.getUuid());

        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                arrow.getX(), arrow.getY(), arrow.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS, 1.0F, 1.4F);
        com.kingdomunique.item.unique.UniqueWeaponTriggers.onBowShot(player);
    }

    /** 光の矢の飛翔中パーティクル（毎20ティック）。消えた矢の登録も掃除する。 */
    public static void tickLightArrows(MinecraftServer server) {
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(LIGHT_ARROW_BY_OWNER).entrySet()) {
            ArrowEntity arrow = null;
            ServerWorld arrowWorld = null;
            for (ServerWorld world : server.getWorlds()) {
                if (world.getEntity(entry.getValue()) instanceof ArrowEntity found) {
                    arrow = found;
                    arrowWorld = world;
                    break;
                }
            }
            if (arrow == null || !arrow.isAlive()) {
                LIGHT_ARROW_BY_OWNER.remove(entry.getKey());
                continue;
            }
            arrowWorld.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                    arrow.getX(), arrow.getY(), arrow.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
        }
    }

    /** 右クリック: 自分の光の矢があるならその座標で太陽の爆発。 */
    public static void detonateLightArrow(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        UUID arrowUuid = LIGHT_ARROW_BY_OWNER.remove(player.getUuid());
        Entity arrow = arrowUuid == null ? null : world.getEntity(arrowUuid);
        if (!(arrow instanceof ArrowEntity) || !arrow.isAlive()) {
            player.sendMessage(Text.literal("§c[太陽弓] 飛翔中の光の矢がありません。"), true);
            return;
        }

        Vec3d pos = arrow.getPos();
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(pos.x - 5.0D, pos.y - 5.0D, pos.z - 5.0D,
                        pos.x + 5.0D, pos.y + 5.0D, pos.z + 5.0D),
                living -> living != player && living.isAlive())) {
            target.damage(target.getDamageSources().mobAttack(player), 15.0F);
            target.setOnFireFor(5);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
        }
        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                pos.x, pos.y, pos.z, 60, 1.5, 1.5, 1.5, 0.25);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                pos.x, pos.y, pos.z, 30, 1.0, 1.0, 1.0, 0.15);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, 1.0F, 1.2F);
        arrow.discard();
        player.sendMessage(Text.literal("§6[太陽の爆発] 光の矢が起爆した！"), true);
    }
}
