package com.kingdomunique.item.unique;

import com.kingdomunique.data.ArmorAcquisitionData;
import com.kingdomunique.data.WorldUniqueData;
import net.minecraft.block.Blocks;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.Map;
import java.util.WeakHashMap;

public final class UniqueArmorTriggers {
    private static final Map<ServerPlayerEntity, Fall> FALLS = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Ride> RIDES = new WeakHashMap<>();

    private UniqueArmorTriggers() {
    }

    public static void onKill(ServerPlayerEntity player, LivingEntity victim, DamageSource source) {
        if (victim.getType() == EntityType.WARDEN) {
            grant(player, UniqueArmors.HADES_HELMET);
        } else if (victim.getType() == EntityType.RAVAGER && source.isOf(DamageTypes.PLAYER_ATTACK)
                && source.getSource() == player && player.getMainHandStack().isEmpty()) {
            grant(player, UniqueArmors.NEMEAN_LION_HIDE);
        } else if (victim.getType() == EntityType.PHANTOM && canEarn(player, UniqueArmors.HERMES_TALARIA)
                && UniqueWeaponTriggers.isOverworldNight(victim.getWorld())
                && ArmorAcquisitionData.get(player).recordPhantom(player,
                        Math.floorDiv(victim.getWorld().getTimeOfDay(), 24000L)) >= 10) {
            grant(player, UniqueArmors.HERMES_TALARIA);
        }
    }

    public static void onEnchant(ServerPlayerEntity player, ServerWorld world, BlockPos table) {
        if (canEarn(player, UniqueArmors.MIMIR_HELMET)
                && EnchantingTableBlock.POWER_PROVIDER_OFFSETS.stream()
                        .filter(offset -> world.getBlockState(table.add(offset)).isOf(Blocks.BOOKSHELF))
                        .filter(offset -> EnchantingTableBlock.canAccessPowerProvider(world, table, offset))
                        .limit(15).count() == 15
                && ArmorAcquisitionData.get(player).recordEnchant(player, table) >= 10) {
            grant(player, UniqueArmors.MIMIR_HELMET);
        }
    }

    public static void onVaultUnlocked(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (canEarn(player, UniqueArmors.JOTUN_LEGGINGS)
                && world.getStructureAccessor().getStructureContaining(pos,
                        entry -> entry.matchesKey(StructureKeys.TRIAL_CHAMBERS)).hasChildren()
                && ArmorAcquisitionData.get(player).recordVault(player, pos) >= 5) {
            grant(player, UniqueArmors.JOTUN_LEGGINGS);
        }
    }

    public static void tick(ServerPlayerEntity player) {
        tickFall(player);
        tickStrider(player);
    }

    public static void resetTravel(ServerPlayerEntity player) {
        FALLS.remove(player);
        RIDES.remove(player);
    }

    private static void tickFall(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD || !canEarn(player, UniqueArmors.DRAGONSCALE_WINGPLATE)
                || !player.isAlive() || player.hasVehicle() || player.getAbilities().flying
                || player.isFallFlying() || player.isClimbing() || player.hasStatusEffect(StatusEffects.LEVITATION)) {
            FALLS.remove(player);
            return;
        }

        double y = player.getY();
        if (y >= world.getTopY()) {
            FALLS.put(player, new Fall(y, 0));
            return;
        }

        Fall fall = FALLS.get(player);
        if (fall == null) {
            return;
        }
        double drop = fall.y() - y;
        int stationaryTicks = drop < 0.001 ? fall.stationaryTicks() + 1 : 0;
        if (drop > 16 || drop < -0.001 || stationaryTicks > 4) {
            FALLS.remove(player);
            return;
        }

        if (player.isOnGround() || player.isTouchingWater() || player.isInLava()) {
            FALLS.remove(player);
            if (y <= world.getBottomY() + 2 && (player.isOnGround() || player.isTouchingWater())) {
                grant(player, UniqueArmors.DRAGONSCALE_WINGPLATE);
            }
            return;
        }

        FALLS.put(player, new Fall(y, stationaryTicks));
    }

    private static void tickStrider(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (!canEarn(player, UniqueArmors.SALAMANDER_BOOTS)
                || !(player.getVehicle() instanceof StriderEntity strider)
                || !(strider.isInLava() || world.getFluidState(strider.getBlockPos()).isIn(FluidTags.LAVA)
                        || world.getFluidState(strider.getBlockPos().down()).isIn(FluidTags.LAVA))) {
            RIDES.remove(player);
            return;
        }

        Vec3d pos = strider.getPos();
        Ride previous = RIDES.put(player, new Ride(world, strider.getId(), pos));
        if (previous == null || previous.world() != world || previous.striderId() != strider.getId()) {
            return;
        }
        double distance = pos.subtract(previous.pos()).horizontalLength();
        if (distance > 0 && distance <= 4
                && ArmorAcquisitionData.get(player).recordLavaDistance(player, distance) >= 333) {
            grant(player, UniqueArmors.SALAMANDER_BOOTS);
        }
    }

    private static boolean canEarn(ServerPlayerEntity player, UniqueArmorItem item) {
        return !player.isCreative() && !player.isSpectator()
                && !ownership(player).isClaimed(Registries.ITEM.getId(item).getPath());
    }

    private static WorldUniqueData ownership(ServerPlayerEntity player) {
        return WorldUniqueData.getOrCreate(player.getServer().getOverworld());
    }

    private static void grant(ServerPlayerEntity player, UniqueArmorItem item) {
        if (!canEarn(player, item) || !ownership(player).claimId(player.getUuid(), Registries.ITEM.getId(item).getPath())) {
            return;
        }

        ItemStack stack = new ItemStack(item);
        if (!player.giveItemStack(stack)) {
            player.dropItem(stack, false);
        }
        player.getServer().getPlayerManager().broadcast(Text.translatable("message.kingdomunique.armor.acquired",
                player.getDisplayName(), item.getName()).formatted(Formatting.GOLD, Formatting.BOLD), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private record Fall(double y, int stationaryTicks) {
    }

    private record Ride(ServerWorld world, int striderId, Vec3d pos) {
    }
}
