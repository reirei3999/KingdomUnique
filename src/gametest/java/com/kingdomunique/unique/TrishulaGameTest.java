package com.kingdomunique.unique;

import com.kingdomunique.data.TrishulaAcquisitionData;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.unique.TrishulaEffects;
import com.kingdomunique.item.unique.TrishulaEntity;
import com.kingdomunique.item.unique.UniqueWeaponTriggers;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.structure.NetherFortressGenerator;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.List;
import java.util.UUID;

public final class TrishulaGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "trishula_hazard")
    public void landingRejectsDamagingBlocksAtFeetHeadAndFloor(TestContext context) {
        var player = player(context);
        var world = context.getWorld();
        BlockPos feet = context.getAbsolutePos(new BlockPos(2, 12, 2));
        world.setBlockState(feet.down(), Blocks.STONE.getDefaultState());
        world.setBlockState(feet, Blocks.AIR.getDefaultState());
        world.setBlockState(feet.up(), Blocks.AIR.getDefaultState());
        try {
            context.assertTrue(TrishulaEffects.isSafeLanding(world, player, feet), "Clear stone footing must be safe");
            for (var hazard : List.of(Blocks.SWEET_BERRY_BUSH, Blocks.CACTUS, Blocks.FIRE, Blocks.SOUL_FIRE,
                    Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.POWDER_SNOW, Blocks.WITHER_ROSE,
                    Blocks.POINTED_DRIPSTONE)) {
                world.setBlockState(feet.down(), Blocks.SOUL_SOIL.getDefaultState());
                var state = hazard == Blocks.SWEET_BERRY_BUSH
                        ? hazard.getDefaultState().with(net.minecraft.block.SweetBerryBushBlock.AGE, 3)
                        : hazard.getDefaultState();
                world.setBlockState(feet, state);
                context.assertTrue(world.getBlockState(feet).isOf(hazard), "Feet fixture must retain " + hazard);
                context.assertFalse(TrishulaEffects.isSafeLanding(world, player, feet), hazard + " at feet is unsafe");
                world.setBlockState(feet, Blocks.AIR.getDefaultState());
                if (hazard != Blocks.FIRE && hazard != Blocks.SOUL_FIRE) {
                    world.setBlockState(feet.up(), state);
                    context.assertTrue(world.getBlockState(feet.up()).isOf(hazard), "Head fixture must retain " + hazard);
                    context.assertFalse(TrishulaEffects.isSafeLanding(world, player, feet), hazard + " at head is unsafe");
                    world.setBlockState(feet.up(), Blocks.AIR.getDefaultState());
                }
            }
            world.setBlockState(feet.down(), Blocks.MAGMA_BLOCK.getDefaultState());
            context.assertFalse(TrishulaEffects.isSafeLanding(world, player, feet), "Magma footing is unsafe");
            world.setBlockState(feet.down(), Blocks.STONE.getDefaultState());
            context.assertTrue(TrishulaEffects.isSafeLanding(world, player, feet), "Ordinary footing remains valid");
            context.complete();
        } finally {
            world.setBlockState(feet.down(), Blocks.AIR.getDefaultState());
            world.setBlockState(feet, Blocks.AIR.getDefaultState());
            world.setBlockState(feet.up(), Blocks.AIR.getDefaultState());
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "trishula_no_landing")
    public void noSafeLandingOrMissingOwnerDoesNotWarpOrProtect(TestContext context) {
        var player = player(context);
        Vec3d original = player.getPos();
        Vec3d impact = context.getAbsolute(new Vec3d(2.5, 16, 2.5));
        var projectile = new TrishulaEntity(context.getWorld(), player, new ItemStack(UniqueWeapons.TRISHULA));
        TrishulaEffects.impact(projectile, impact);
        context.assertEquals(player.getPos(), original, "An impact without safe footing must not warp");
        context.assertFalse(TrishulaEffects.isProtected(player, player.getDamageSources().onFire()),
                "A failed warp must not grant protection");
        projectile.setOwner(null);
        TrishulaEffects.impact(projectile, impact);
        context.assertEquals(player.getPos(), original, "An ownerless impact must not warp");
        projectile.discard();
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "trishula_damage")
    public void doublesMeleeAndThrownDamageOnlyAgainstMobs(TestContext context) {
        var player = player(context);
        var mob = context.spawnMob(EntityType.COW, 2, 1, 2);
        mob.setAiDisabled(true);
        var victim = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(UniqueWeapons.TRISHULA));
        mob.damage(context.getWorld().getDamageSources().playerAttack(player), 2);
        context.assertTrue(mob.getHealth() == mob.getMaxHealth() - 4, "Melee must deal double damage to mobs");
        victim.damage(context.getWorld().getDamageSources().playerAttack(player), 2);
        context.assertTrue(victim.getHealth() == victim.getMaxHealth() - 2, "Players must take normal melee damage");
        mob.timeUntilRegen = 0;
        victim.timeUntilRegen = 0;
        var projectile = new TrishulaEntity(context.getWorld(), player, player.getMainHandStack().copy());
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
        var source = context.getWorld().getDamageSources().trident(projectile, player);
        mob.damage(source, 2);
        victim.damage(source, 2);
        context.assertTrue(mob.getHealth() == mob.getMaxHealth() - 8, "Thrown damage must double even after switching hands");
        context.assertTrue(victim.getHealth() == victim.getMaxHealth() - 4, "Players must take normal thrown damage");
        projectile.discard();
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "trishula_impact", tickLimit = 220)
    public void impactWarpsWithoutDestroyingBlocksAndProtectionExpires(TestContext context) {
        var player = player(context);
        for (int tick = 0; tick < 65; tick++) {
            player.tick();
        }
        Vec3d start = context.getAbsolute(new Vec3d(1.5, 2, 1.5));
        player.refreshPositionAndAngles(start.x, start.y, start.z, 0, 0);
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                context.setBlockState(x, 1, z, Blocks.STONE);
                for (int y = 2; y <= 4; y++) {
                    context.setBlockState(x, y, z, Blocks.AIR);
                }
            }
        }
        context.setBlockState(4, 2, 4, Blocks.GLASS);
        context.setBlockState(4, 3, 4, Blocks.GLASS);
        var shot = new java.util.concurrent.atomic.AtomicReference<TrishulaEntity>();
        context.runAtTick(65, () -> {
            player.refreshPositionAndAngles(start.x, start.y, start.z, 0, 0);
            player.setHealth(20);
            player.fallDistance = 15;
            player.setOnFireFor(10);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(UniqueWeapons.TRISHULA));
            UniqueWeapons.TRISHULA.use(context.getWorld(), player, Hand.MAIN_HAND);
            context.assertTrue(player.getMainHandStack().isOf(UniqueWeapons.TRISHULA)
                    && player.getMainHandStack().getCount() == 1, "Throw must retain the item");
            context.assertTrue(player.getItemCooldownManager().isCoolingDown(UniqueWeapons.TRISHULA),
                    "Throw must start its cooldown");
            var projectiles = context.getWorld().getEntitiesByClass(TrishulaEntity.class,
                    player.getBoundingBox().expand(3), entity -> true);
            context.assertEquals(projectiles.size(), 1, "Throw must spawn one projectile");
            var projectile = projectiles.getFirst();
            context.assertEquals(projectile.pickupType,
                    net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.DISALLOWED,
                    "Projectile cannot be picked up");
            UniqueWeapons.TRISHULA.use(context.getWorld(), player, Hand.MAIN_HAND);
            context.assertEquals(context.getWorld().getEntitiesByClass(TrishulaEntity.class,
                    player.getBoundingBox().expand(3), entity -> true).size(), 1, "Cooldown must prevent another throw");
            shot.set(projectile);
            Vec3d launch = context.getAbsolute(new Vec3d(4.5, 2.5, 2.5));
            projectile.setPosition(launch);
            projectile.setVelocity(0, 0, 3);
        });
        context.runAtTick(68, () -> {
            var projectile = shot.get();
            Vec3d impact = context.getAbsolute(new Vec3d(4.5, 2, 3.5));
            context.assertTrue(projectile.isRemoved(), "Impact must consume the projectile");
            context.assertTrue(player.getPos().distanceTo(impact) < 2, "Impact must warp the thrower nearby");
            context.assertTrue(player.getHealth() == 20 && player.fallDistance == 0 && !player.isOnFire(),
                    "Thrower must be unharmed, extinguished, and have no accumulated fall");
            context.assertTrue(context.getBlockState(new BlockPos(4, 2, 4)).isOf(Blocks.GLASS),
                    "Explosion must not destroy fragile blocks");
            context.assertFalse(player.damage(player.getDamageSources().onFire(), 2), "Burn damage must be blocked");
            context.assertFalse(player.damage(player.getDamageSources().fall(), 2), "Fall damage must be blocked");
            context.assertFalse(TrishulaEffects.isProtected(player, player.getDamageSources().generic()),
                    "Other damage must not be protected");
        });
        context.runAtTick(166, () -> {
            player.timeUntilRegen = 0;
            context.assertTrue(player.damage(player.getDamageSources().onFire(), 2), "Fire protection must expire");
            player.timeUntilRegen = 0;
            context.assertTrue(player.damage(player.getDamageSources().fall(), 2), "Fall protection must expire");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "trishula_acquisition")
    public void acquisitionRequiresWitherThenThreeDistinctFortressBlazeSpawners(TestContext context) {
        var player = player(context);
        var nether = context.getWorld().getServer().getWorld(World.NETHER);
        var unique = WorldUniqueData.getOrCreate(context.getWorld());
        UUID oldOwner = unique.getOwner("trishula");
        UUID oldMjolnirOwner = unique.getOwner("mjolnir");
        if (oldOwner != null) {
            unique.releaseId(oldOwner, "trishula");
        }
        BlockPos base = new BlockPos((16000 + context.getAbsolutePos(BlockPos.ORIGIN).getX()) & ~15, 100, 16000);
        var fortress = nether.getRegistryManager().get(RegistryKeys.STRUCTURE).get(StructureKeys.FORTRESS);
        var chunk = nether.getChunk(base);
        var section = ChunkSectionPos.from(base);
        var accessor = nether.getStructureAccessor();
        var savedStarts = new java.util.HashMap<>(chunk.getStructureStarts());
        var savedReferences = new java.util.HashMap<>(chunk.getStructureReferences());
        var box = new BlockBox(base.getX(), base.getY(), base.getZ(), base.getX() + 4, base.getY() + 3, base.getZ() + 4);
        var bridge = new NetherFortressGenerator.Bridge(0, nether.random, box, Direction.NORTH);
        var start = new StructureStart(fortress, new ChunkPos(base), 0, new StructurePiecesList(List.of(bridge)));
        try {
            accessor.setStructureStart(section, fortress, start, chunk);
            accessor.addStructureReference(section, fortress, new ChunkPos(base).toLong(), chunk);
            breakSpawner(nether, player, base, EntityType.BLAZE);
            UniqueWeaponTriggers.onKill(player, EntityType.WITHER.create(context.getWorld()));
            var data = TrishulaAcquisitionData.getOrCreate(nether);
            var restored = new TrishulaAcquisitionData(data.writeNbt(new NbtCompound(), nether.getRegistryManager()));
            context.assertEquals(restored.recordSpawner(player.getUuid(), base.asLong()), 1,
                    "Before-Wither break must not count; Wither flag must survive reload");
            breakSpawner(nether, player, base.add(0, 0, 1), EntityType.PIG);
            breakSpawner(nether, player, base.add(0, 0, 8), EntityType.BLAZE);
            breakSpawner(context.getWorld(), player, context.getAbsolutePos(new BlockPos(1, 2, 1)), EntityType.BLAZE);
            breakSpawner(nether, player, base, EntityType.BLAZE);
            breakSpawner(nether, player, base, EntityType.BLAZE);
            breakSpawner(nether, player, base.add(1, 0, 0), EntityType.BLAZE);
            context.assertTrue(unique.getOwner("trishula") == null,
                    "Duplicates, other mobs, other dimensions, and non-fortress spawners must not count");
            var reload = new TrishulaAcquisitionData(data.writeNbt(new NbtCompound(), nether.getRegistryManager()));
            context.assertEquals(reload.recordSpawner(player.getUuid(), base.asLong()), 2, "Positions must survive reload");
            areaMineSpawner(context, nether, player, base.add(2, 0, 0));
            context.assertTrue(player.getUuid().equals(unique.getOwner("trishula")), "Third qualifying break must grant");
            context.assertTrue(player.getInventory().contains(new ItemStack(UniqueWeapons.TRISHULA)),
                    "Acquisition must grant the physical weapon");
            context.complete();
        } finally {
            unique.releaseId(player.getUuid(), "trishula");
            if (oldOwner != null) {
                unique.claimId(oldOwner, "trishula");
            }
            if (oldMjolnirOwner == null) {
                unique.releaseId(player.getUuid(), "mjolnir");
            }
            chunk.setStructureStarts(savedStarts);
            chunk.setStructureReferences(savedReferences);
        }
    }

    private static void areaMineSpawner(TestContext context, net.minecraft.server.world.ServerWorld world,
                                        ServerPlayerEntity player, BlockPos spawnerPos) {
        var ownership = WorldUniqueData.getOrCreate(context.getWorld());
        String smith = com.kingdomunique.registry.UniqueContentRegistry.DIVINE_SMITH;
        UUID oldOwner = ownership.getOwner(smith);
        if (oldOwner != null) {
            ownership.releaseId(oldOwner, smith);
        }
        ownership.claimId(player.getUuid(), smith);
        var ability = com.kingdomunique.ability.AbilityState.get(player.getUuid());
        int originalRange = ability.getMiningRange();
        while (ability.getMiningRange() != 3) {
            ability.cycleMiningRange();
        }
        BlockPos center = spawnerPos.south();
        var originalStates = new java.util.HashMap<BlockPos, net.minecraft.block.BlockState>();
        for (BlockPos pos : BlockPos.iterate(center.add(-1, 0, -1), center.add(1, 0, 1))) {
            originalStates.put(pos.toImmutable(), world.getBlockState(pos));
        }
        try {
            originalStates.keySet().forEach(pos -> world.setBlockState(pos, Blocks.AIR.getDefaultState()));
            world.setBlockState(center, Blocks.STONE.getDefaultState());
            world.setBlockState(spawnerPos, Blocks.SPAWNER.getDefaultState());
            ((MobSpawnerBlockEntity) world.getBlockEntity(spawnerPos)).setEntityType(EntityType.BLAZE, world.random);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
            player.interactionManager.setWorld(world);
            context.assertTrue(player.interactionManager.tryBreakBlock(center), "Area mining center must break");
            context.assertTrue(world.isAir(spawnerPos), "Area mining must break the adjacent blaze spawner");
        } finally {
            player.interactionManager.setWorld(player.getServerWorld());
            originalStates.forEach(world::setBlockState);
            while (ability.getMiningRange() != originalRange) {
                ability.cycleMiningRange();
            }
            ownership.releaseId(player.getUuid(), smith);
            if (oldOwner != null) {
                ownership.claimId(oldOwner, smith);
            }
        }
    }

    private static void breakSpawner(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity player,
                                     BlockPos pos, EntityType<?> type) {
        var spawner = new MobSpawnerBlockEntity(pos, Blocks.SPAWNER.getDefaultState());
        spawner.setWorld(world);
        spawner.setEntityType(type, world.random);
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(world, player, pos,
                Blocks.SPAWNER.getDefaultState(), spawner);
    }

    private static ServerPlayerEntity player(TestContext context) {
        var player = new ServerPlayerEntity(context.getWorld().getServer(), context.getWorld(),
                new GameProfile(UUID.randomUUID(), "trishula_test"), SyncedClientOptions.createDefault()) {
            @Override
            protected ItemCooldownManager createCooldownManager() {
                return new ItemCooldownManager();
            }

            @Override
            public void sendMessage(Text text, boolean overlay) {
            }
        };
        new ServerPlayNetworkHandler(context.getWorld().getServer(), new ClientConnection(NetworkSide.SERVERBOUND),
                player, ConnectedClientData.createDefault(player.getGameProfile(), false));
        Vec3d position = context.getAbsolute(new Vec3d(1.5, 2, 1.5));
        player.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
        return player;
    }
}
