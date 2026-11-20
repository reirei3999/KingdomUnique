package com.kingdomunique.building;

import com.kingdomunique.effect.RelicEffects;
import com.kingdomunique.entity.CitizenVillager;
import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.kingdom.KingdomManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;

/** 建築コスト／時間の倍率と自動建築の進行管理。 */
public final class BuildingManager {

    private BuildingManager() {
    }

    /** 必要資材コスト倍率（現場監督30%OFF、ヘファイストス20%OFF）。 */
    public static double costMultiplier(ServerPlayerEntity player) {
        double multiplier = 1.0D;
        if (unique(player, "site_supervisor")) {
            multiplier -= 0.30D;
        }
        if (hasHephaestus(player)) {
            multiplier -= 0.20D;
        }
        return Math.max(0.4D, multiplier);
    }

    /** 建築時間倍率（現場監督30%、設計盤50%、ヘファイストス30%短縮）。 */
    public static double timeMultiplier(ServerPlayerEntity player) {
        double multiplier = RelicEffects.getBuildTimeMultiplier(player)
                * com.kingdomunique.kingdom.PolicyManager.buildTimeMultiplier(
                        player.getServerWorld(), player.getUuid());
        if (unique(player, "site_supervisor")) {
            multiplier -= 0.30D;
        }
        if (hasHephaestus(player)) {
            multiplier -= 0.30D;
        }
        return Math.max(0.15D, multiplier);
    }

    /** 建築士の作業効率（設計盤2倍、ヘファイストス1.5倍）。 */
    public static double builderEfficiency(ServerPlayerEntity player) {
        double value = RelicEffects.getBuilderEfficiencyMultiplier(player);
        if (hasHephaestus(player)) {
            value *= 1.5D;
        }
        return value;
    }

    private static boolean unique(ServerPlayerEntity player, String id) {
        com.kingdomunique.data.WorldUniqueData data =
                com.kingdomunique.data.WorldUniqueData.getOrCreate(
                        player.getServer().getWorld(World.OVERWORLD));
        return data.hasId(player.getUuid(), id);
    }

    private static boolean hasHephaestus(ServerPlayerEntity player) {
        for (HeroEntity hero : player.getWorld().getEntitiesByClass(HeroEntity.class,
                player.getBoundingBox().expand(128.0D), h -> h.isAlive())) {
            if (hero.getHeroClass() == HeroClass.SMITH && player.getUuid().equals(hero.getOwnerUuid())) {
                return true;
            }
        }
        return false;
    }

    /** 隣接チェストから資材を消費する。 */
    public static boolean consumeMaterials(Inventory chest, BuildingType type, ServerPlayerEntity player) {
        double multiplier = costMultiplier(player);
        for (Map.Entry<Item, Integer> entry : type.materials().entrySet()) {
            int needed = Math.max(1, (int) Math.ceil(entry.getValue() * multiplier));
            int available = 0;
            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isOf(entry.getKey())) {
                    available += stack.getCount();
                }
            }
            if (available < needed) {
                player.sendMessage(Text.literal("§c[設計図] " + entry.getKey().getName().getString()
                        + " が " + needed + " 個必要です。"), false);
                return false;
            }
            int remaining = needed;
            for (int i = 0; i < chest.size() && remaining > 0; i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isOf(entry.getKey())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.decrement(take);
                    remaining -= take;
                }
            }
            chest.markDirty();
        }
        return true;
    }

    /** 毎Tickの建築進行。 */
    public static void onServerTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            BuildData data = BuildData.getOrCreate(world);
            for (BuildData.BuildSite site : new java.util.ArrayList<>(data.sites())) {
                if (!site.ready()) {
                    continue;
                }
                if (!world.isChunkLoaded(site.pos())) {
                    continue;
                }
                ServerPlayerEntity owner = null;
                if (world.getEntity(site.owner()) instanceof ServerPlayerEntity player) {
                    owner = player;
                }
                if (owner == null) {
                    continue;
                }
                if (!hasBuilderNearby(world, site.pos(), owner)) {
                    continue;
                }

                int required = (int) Math.max(20L, site.type().getBaseBuildTicks() * timeMultiplier(owner));
                int progress = site.progress() + 1;
                if (progress % 20 == 0) {
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            site.pos().getX() + 0.5, site.pos().getY() + 1.0, site.pos().getZ() + 0.5,
                            8, 0.5, 0.5, 0.5, 0.05);
                    world.playSound(null, site.pos(), SoundEvents.BLOCK_ANVIL_USE,
                            SoundCategory.BLOCKS, 0.6F, 1.0F);
                }
                if (progress >= required) {
                    StructurePlacer.place(world, site.pos(), site.type());
                    DefenseData.register(world, site.pos(), site.type(), owner.getUuid());
                    if (site.type() == BuildingType.TOWN_HALL) {
                        KingdomManager.markTownHall(owner, site.pos());
                        com.kingdomunique.kingdom.PolicyManager.sendPolicyOffer(owner);
                    } else if (site.type().getPopulationCapBonus() > 0) {
                        KingdomManager.addCapBonus(owner, site.type().getPopulationCapBonus());
                    }
                    world.playSound(null, site.pos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                            SoundCategory.BLOCKS, 1.0F, 1.0F);
                    owner.sendMessage(Text.literal("§6[設計図] " + site.type().getDisplayName()
                            + " が完成しました！"), false);
                    data.sites().remove(site);
                    data.markChanged();
                } else {
                    data.sites().remove(site);
                    data.add(site.withProgress(progress));
                }
            }
        }
    }

    /** 建築サイトの完了までに必要なティック数（依頼者の倍率を反映）。 */
    public static int requiredTicks(ServerWorld world, BuildData.BuildSite site) {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(site.owner());
        double multiplier = owner != null ? timeMultiplier(owner) : 1.0D;
        return (int) Math.max(20L, site.type().getBaseBuildTicks() * multiplier);
    }

    private static boolean hasBuilderNearby(ServerWorld world, BlockPos pos, ServerPlayerEntity owner) {
        for (net.minecraft.entity.passive.VillagerEntity villager : world.getEntitiesByClass(
                net.minecraft.entity.passive.VillagerEntity.class,
                new net.minecraft.util.math.Box(pos).expand(24.0D),
                v -> v.isAlive() && v instanceof CitizenVillager)) {
            CitizenVillager citizen = (CitizenVillager) villager;
            if (owner.getUuid().toString().equals(citizen.ku$getOwnerUuid())
                    && CitizenVillager.ROLE_BUILDER.equals(citizen.ku$getRole())) {
                return true;
            }
        }
        return false;
    }
}
