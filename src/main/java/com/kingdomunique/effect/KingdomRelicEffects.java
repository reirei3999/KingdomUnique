package com.kingdomunique.effect;

import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.entity.KingdomGuardianEntity;
import com.kingdomunique.entity.ModEntities;
import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomManager;
import com.kingdomunique.registry.RelicRegistry;
import com.kingdomunique.war.KingdomWarTargetHelper;
import com.kingdomunique.world.PlayerSpawnData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class KingdomRelicEffects {
    private static final long DAY_TICKS = 24000L;
    private static final int SURVEY_INTERVAL = 10;
    private static final int GLOW_TICKS = 2 * SURVEY_INTERVAL;
    private static final int LANDING_RADIUS = 4;
    private static final WeakHashMap<ServerWorld, Intruders> INTRUDERS = new WeakHashMap<>();

    private record Intruders(UUID owner, Set<UUID> entities) {
    }

    private KingdomRelicEffects() {
    }

    public static void registerCommands(LiteralArgumentBuilder<ServerCommandSource> ku) {
        ku.then(CommandManager.literal("recall").executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            return recall(player) ? 1 : 0;
        }));
    }

    public static void onServerTick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        WorldUniqueData ownership = WorldUniqueData.getOrCreate(world);
        if (world.getTime() % SURVEY_INTERVAL == 0) {
            UUID owner = ownership.getOwner(RelicRegistry.CRYSTAL);
            ServerPlayerEntity player = owner == null ? null : server.getPlayerManager().getPlayer(owner);
            if (player == null) {
                INTRUDERS.remove(world);
            } else {
                survey(world, player);
            }
        }
        if (world.getTime() % 20 == 0) {
            UUID owner = ownership.getOwner(RelicRegistry.GUARDIAN_EGG);
            if (owner != null) {
                spawnGuardian(world, owner);
            }
        }
    }

    static int survey(ServerWorld world, ServerPlayerEntity player) {
        UUID owner = player.getUuid();
        BlockPos center = KingdomManager.centerOf(world, owner);
        if (center == null) {
            INTRUDERS.remove(world);
            return 0;
        }
        int radius = KingdomManager.territoryRadius(world, owner);
        Box area = new Box(center.getX() - radius, world.getBottomY(), center.getZ() - radius,
                center.getX() + radius + 1, world.getTopY(), center.getZ() + radius + 1);
        Intruders last = INTRUDERS.get(world);
        Set<UUID> previous = last != null && owner.equals(last.owner()) ? last.entities() : Set.of();
        Set<UUID> present = new HashSet<>();
        int arrivals = 0;
        Text first = null;
        for (LivingEntity intruder : world.getEntitiesByClass(LivingEntity.class, area,
                entity -> (entity instanceof ServerPlayerEntity || isHostileMob(entity))
                        && isTerritoryEnemy(world, player, owner, entity))) {
            present.add(intruder.getUuid());
            intruder.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GLOW_TICKS,
                    0, false, false, false));
            if (!previous.contains(intruder.getUuid())) {
                if (first == null) {
                    first = intruder.getDisplayName();
                }
                arrivals++;
            }
        }
        INTRUDERS.put(world, new Intruders(owner, present));
        if (first != null) {
            String key = arrivals == 1 ? "crystal.intrusion" : "crystal.intrusions";
            player.sendMessage(message(key, first, arrivals - 1).formatted(Formatting.AQUA), false);
        }
        return arrivals;
    }

    public static boolean isTerritoryEnemy(ServerWorld world, LivingEntity defender, UUID owner,
                                           LivingEntity candidate) {
        return KingdomManager.isInTerritory(world, owner, candidate.getPos())
                && (isHostileMob(candidate) || KingdomWarTargetHelper.isValidEnemy(world, defender, owner, candidate));
    }

    private static boolean isHostileMob(LivingEntity candidate) {
        return candidate instanceof Monster && candidate.isAlive()
                && !candidate.getCommandTags().contains("allied_mob")
                && !candidate.getCommandTags().contains("kingdomunique_ally")
                && KingdomWarTargetHelper.getOwnerUuid(candidate) == null;
    }

    static boolean recall(ServerPlayerEntity player) {
        if (!RelicEffects.has(player, RelicRegistry.COMPASS)) {
            player.sendMessage(message("compass.required"), false);
            return false;
        }
        if (KingdomManager.possessedHero(player) != null) {
            player.sendMessage(message("compass.possessing"), false);
            return false;
        }
        ServerWorld world = player.getServer().getOverworld();
        UUID owner = player.getUuid();
        KingdomRelicState state = KingdomRelicState.get(world);
        long day = day(world);
        if (!state.canRecall(owner, day)) {
            player.sendMessage(message("compass.used"), false);
            return false;
        }
        BlockPos destination = findLanding(world, owner, player, true);
        if (destination == null || !player.teleport(world, destination.getX() + 0.5, destination.getY(),
                destination.getZ() + 0.5, Set.<PositionFlag>of(), player.getYaw(), player.getPitch())) {
            player.sendMessage(message("compass.no_destination"), false);
            return false;
        }
        player.fallDistance = 0;
        state.recordRecall(owner, day);
        player.sendMessage(message("compass.recalled").formatted(Formatting.AQUA), false);
        return true;
    }

    static KingdomGuardianEntity spawnGuardian(ServerWorld world, UUID owner) {
        KingdomRelicState state = KingdomRelicState.get(world);
        if (!state.canSpawnGuardian(owner, day(world))) {
            return null;
        }
        KingdomGuardianEntity guardian = ModEntities.KINGDOM_GUARDIAN.create(world);
        if (guardian == null) {
            return null;
        }
        BlockPos landing = findLanding(world, owner, guardian, false);
        if (landing == null) {
            return null;
        }
        guardian.addCommandTag("owner_" + owner);
        guardian.setPersistent();
        guardian.refreshPositionAndAngles(landing, 0.0F, 0.0F);
        if (!world.spawnEntity(guardian)) {
            return null;
        }
        state.recordGuardian(owner, guardian.getUuid());
        return guardian;
    }

    public static boolean hasGuardianRelic(ServerWorld world, UUID owner, UUID entity) {
        return WorldUniqueData.getOrCreate(world).hasId(owner, RelicRegistry.GUARDIAN_EGG)
                && KingdomRelicState.get(world).isGuardian(owner, entity);
    }

    public static void guardianRemoved(ServerWorld world, UUID owner, UUID entity) {
        KingdomRelicState.get(world).removeGuardian(owner, entity, day(world) + 1);
    }

    private static long day(ServerWorld world) {
        return Math.floorDiv(world.getServer().getOverworld().getTimeOfDay(), DAY_TICKS);
    }

    private static BlockPos findLanding(ServerWorld world, UUID owner, Entity entity, boolean loadChunks) {
        KingdomData.KingdomInfo kingdom = KingdomData.getOrCreate(world).get(owner);
        if (kingdom == null || !kingdom.centerSet()) {
            return null;
        }
        int x = kingdom.centerX();
        int z = kingdom.centerZ();
        if (!loadChunks && !world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        BlockPos spawn = PlayerSpawnData.getOrCreate(world).get(owner);
        if (spawn != null && spawn.getX() == x && spawn.getZ() == z && canLand(world, entity, spawn)) {
            return spawn;
        }
        for (BlockPos column : BlockPos.iterateOutwards(new BlockPos(x, 0, z), LANDING_RADIUS, 0, LANDING_RADIUS)) {
            if (!loadChunks && !world.getChunkManager().isChunkLoaded(column.getX() >> 4, column.getZ() >> 4)) {
                continue;
            }
            BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (canLand(world, entity, surface)) {
                return surface;
            }
        }
        return null;
    }

    private static boolean canLand(ServerWorld world, Entity entity, BlockPos pos) {
        Box box = entity.getType().getDimensions().getBoxAt(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (pos.getY() <= world.getBottomY() || box.maxY > world.getTopY()
                || !world.getWorldBorder().contains(box)) {
            return false;
        }
        BlockPos floor = pos.down();
        var ground = world.getBlockState(floor);
        return ground.isSolidBlock(world, floor) && !ground.isOf(Blocks.MAGMA_BLOCK)
                && !ground.isOf(Blocks.CACTUS) && world.isAir(pos)
                && world.isSpaceEmpty(entity, box) && !world.containsFluid(box);
    }

    private static MutableText message(String suffix, Object... args) {
        return Text.translatable("message.kingdomunique.relic." + suffix, args);
    }
}
