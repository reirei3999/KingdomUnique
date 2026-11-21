package com.kingdomunique.item.unique;

import com.kingdomunique.component.ModDataComponents;
import com.kingdomunique.data.PlayerStatsData;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.ItemCatalog;
import com.kingdomunique.kingdom.KingdomManager;
import com.kingdomunique.registry.ContentRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 6種のユニーク武器の取得条件・排他付与・演出を管理する。 */
public final class UniqueWeaponTriggers {

    private static final int GA_BOLG_REQUIRED_TICKS = 60;
    private static final Map<UUID, Integer> GA_BOLG_PROGRESS = new HashMap<>();
    private static final int MURAMASA_KILLS = 100;

    private UniqueWeaponTriggers() {
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // エクスカリバー: 自国の人口25人
            if (!isClaimed(player, "excalibur")
                    && KingdomManager.getOrCreate(player).population() >= 25) {
                grant(player, "excalibur");
            }
            tickGaeBolg(player);
        }
    }

    /** ゲイボルグ: 満月の夜にスニークしながら空を見上げ続けること3秒。 */
    private static void tickGaeBolg(ServerPlayerEntity player) {
        if (isClaimed(player, "gae_bolg")) {
            GA_BOLG_PROGRESS.remove(player.getUuid());
            return;
        }
        ServerWorld world = player.getServerWorld();
        boolean ok = world.getRegistryKey() == World.OVERWORLD
                && world.isNight()
                && world.getMoonPhase() == 0
                && world.isSkyVisible(player.getBlockPos())
                && player.isSneaking()
                && player.getPitch() <= -60.0F;
        if (!ok) {
            GA_BOLG_PROGRESS.remove(player.getUuid());
            return;
        }
        int progress = GA_BOLG_PROGRESS.getOrDefault(player.getUuid(), 0) + 1;
        GA_BOLG_PROGRESS.put(player.getUuid(), progress);
        player.sendMessage(Text.literal("§b満月の光が槍の形を結びつつある… " + progress + "/60")
                .formatted(Formatting.AQUA), true);
        if (progress >= GA_BOLG_REQUIRED_TICKS) {
            GA_BOLG_PROGRESS.remove(player.getUuid());
            grant(player, "gae_bolg");
        }
    }

    /** キル時: ムラマサ（100体）・ミョルニル（Worthy/ドラゴン）・撃破数記録。 */
    public static void onKill(ServerPlayerEntity player, LivingEntity victim) {
        PlayerStatsData stats = PlayerStatsData.getOrCreate(player.getServerWorld());
        int total = stats.increment(player.getUuid());
        ItemStack held = player.getMainHandStack();

        if (held.isOf(UniqueWeapons.MURAMASA)) {
            int weaponKills = held.getOrDefault(ModDataComponents.TOTAL_KILLS, 0) + 1;
            held.set(ModDataComponents.TOTAL_KILLS, weaponKills);
        }

        if (!isClaimed(player, "muramasa") && total >= MURAMASA_KILLS) {
            grant(player, "muramasa");
        }
        if (!isClaimed(player, "mjolnir")
                && (victim instanceof WitherEntity || victim instanceof EnderDragonEntity)) {
            grant(player, "mjolnir");
        }
    }

    /** アポロンの太陽弓: 高度300以上・昼・上空を見上げて矢を放つ。 */
    public static void onBowShot(ServerPlayerEntity player) {
        if (isClaimed(player, "apollo_bow")) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (world.getRegistryKey() == World.OVERWORLD
                && player.getY() >= 300.0D
                && world.isDay()
                && player.getPitch() <= -60.0F) {
            grant(player, "apollo_bow");
        }
    }

    /** クロノス・ダガー: 無限強化で初めて +4 以上に到達。 */
    public static void onEnhanced(ServerPlayerEntity player, int newLevel) {
        if (newLevel >= 4 && !isClaimed(player, "chronos_dagger")) {
            grant(player, "chronos_dagger");
        }
    }

    private static boolean isClaimed(ServerPlayerEntity player, String id) {
        WorldUniqueData data = WorldUniqueData.getOrCreate(
                player.getServer().getWorld(World.OVERWORLD));
        // 村チェストに置かれたまま誰も拾っていない武器は、取得条件を満たした人が受け取れる。
        if (UniqueWeaponManager.WORLD_OWNER.equals(data.getOwner(id))) {
            return false;
        }
        return data.isClaimed(id);
    }

    /** 排他ロックして付与し、全体アナウンスと演出を行う。 */
    private static void grant(ServerPlayerEntity player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerWorld world = server.getWorld(World.OVERWORLD);
        WorldUniqueData data = WorldUniqueData.getOrCreate(world);
        if (UniqueWeaponManager.WORLD_OWNER.equals(data.getOwner(id))) {
            if (!data.transferId(UniqueWeaponManager.WORLD_OWNER, player.getUuid(), id)) {
                return;
            }
            UniqueWeaponManager.removeFromVillageChests(world, id);
        } else if (!data.claimId(player.getUuid(), id)) {
            return;
        }

        ItemStack stack = ItemCatalog.create(id);
        if (stack.isEmpty() || stack.getItem() == net.minecraft.item.Items.AIR) {
            // カタログに無くても登録済みIDなら必ず有効なスタックを生成する。
            com.kingdomunique.util.RewardUtil.grantPhysical(player, id);
        } else {
            player.giveItemStack(stack);
        }

        String name = ContentRegistry.nameOf(id);
        server.getPlayerManager().broadcast(
                Text.literal("【神話の顕現】" + player.getName().getString()
                        + " が世界に一つだけの神話級武器《" + name + "》を手に入れた！")
                        .formatted(Formatting.GOLD, Formatting.BOLD), false);

        // ダメージなしの落雷演出
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.setCosmetic(true);
            bolt.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
            world.spawnEntity(bolt);
        }
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 1.5, 2.0, 1.5, 0.05);
        world.spawnParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.0, 1.5, 1.0, 0.08);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        if ("gae_bolg".equals(id)) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(),
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }
}
