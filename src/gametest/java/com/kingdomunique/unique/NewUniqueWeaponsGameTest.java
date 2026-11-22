package com.kingdomunique.unique;

import com.kingdomunique.data.WeaponAcquisitionData;
import com.kingdomunique.data.WorldUniqueData;
import com.kingdomunique.item.unique.PoseidonWhirlpool;
import com.kingdomunique.item.unique.UniqueWeaponTriggers;
import com.kingdomunique.item.unique.UniqueWeapons;
import com.kingdomunique.war.DiplomacyData;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.UUID;

public final class NewUniqueWeaponsGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_unique_weapons")
    public void kusanagiSwingDamagesOnlyForwardAndStartsCooldown(TestContext context) {
        var player = player(context, 3.5, 1, 3.5);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(UniqueWeapons.KUSANAGI));
        var front = context.spawnMob(EntityType.PIG, 3, 1, 6);
        var behind = context.spawnMob(EntityType.PIG, 3, 1, 0);
        player.swingHand(Hand.MAIN_HAND);
        context.assertTrue(front.getHealth() < front.getMaxHealth() && front.getVelocity().z > 0,
                "The forward target must take damage and knockback from a swing");
        context.assertTrue(behind.getHealth() == behind.getMaxHealth(), "A target behind the caster must be safe");
        context.assertTrue(player.getItemCooldownManager().isCoolingDown(UniqueWeapons.KUSANAGI),
                "Wind slash must start its cooldown");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_unique_weapons")
    public void poseidonThrowCreatesWhirlpoolOnBlockImpact(TestContext context) {
        var player = player(context, 3.5, 1, 1.5);
        var stack = new ItemStack(UniqueWeapons.POSEIDON_TRIDENT);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        for (int x = 2; x <= 4; x++) {
            for (int y = 1; y <= 3; y++) {
                context.setBlockState(x, y, 6, Blocks.STONE);
            }
        }
        var target = context.spawnMob(EntityType.HUSK, 1, 1, 4);
        target.setAiDisabled(true);
        UniqueWeapons.POSEIDON_TRIDENT.onStoppedUsing(stack, context.getWorld(), player,
                UniqueWeapons.POSEIDON_TRIDENT.getMaxUseTime(stack, player) - 10);
        context.assertTrue(player.getItemCooldownManager().isCoolingDown(UniqueWeapons.POSEIDON_TRIDENT),
                "A successful throw must start the cooldown");
        context.runAtTick(6, () -> {
            PoseidonWhirlpool.tick(context.getWorld());
            context.assertTrue(target.hasStatusEffect(StatusEffects.SLOWNESS),
                    "The throw must restrain nearby enemies after hitting the wall");
            var tridents = context.getWorld().getEntitiesByClass(TridentEntity.class,
                    new Box(player.getPos(), player.getPos()).expand(8), trident -> trident.isAlive());
            tridents.forEach(TridentEntity::discard);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_unique_weapons")
    public void poseidonWhirlpoolSparesKingdomAllies(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        var caster = context.createMockCreativeServerPlayerInWorld();
        var ally = context.createMockPlayer(GameMode.SURVIVAL);
        var allyPos = context.getAbsolute(new Vec3d(2.5, 1, 3.5));
        ally.refreshPositionAndAngles(allyPos.x, allyPos.y, allyPos.z, 0, 0);
        var diplomacy = DiplomacyData.getOrCreate(world);
        boolean wasPvpEnabled = server.isPvpEnabled();
        try {
            server.setPvpEnabled(true);
            context.assertTrue(world.spawnEntity(ally), "The ally must be present in the world");
            context.assertTrue(caster.shouldDamagePlayer(ally) && !caster.isTeammate(ally),
                    "Vanilla PvP must allow targeting the kingdom ally");
            diplomacy.ally(caster.getUuid(), ally.getUuid());
            var enemy = context.spawnMob(EntityType.HUSK, 4, 1, 3);
            PoseidonWhirlpool.start(world, caster.getUuid(), context.getAbsolute(new Vec3d(3.5, 2, 3.5)));
            PoseidonWhirlpool.tick(world);
            context.assertTrue(enemy.hasStatusEffect(StatusEffects.SLOWNESS), "A nearby enemy must be restrained");
            context.assertTrue(!ally.hasStatusEffect(StatusEffects.SLOWNESS) && ally.getVelocity().equals(Vec3d.ZERO),
                    "The whirlpool must not pull or slow a kingdom ally");
            context.complete();
        } finally {
            server.setPvpEnabled(wasPvpEnabled);
            ally.discard();
            diplomacy.declareWar(caster.getUuid(), ally.getUuid());
            var unique = WorldUniqueData.getOrCreate(world);
            String awakening = unique.getClaimedId(caster.getUuid());
            if (awakening != null) {
                unique.releaseId(caster.getUuid(), awakening);
            }
            server.getPlayerManager().remove(caster);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_unique_weapons")
    public void scytheDealsPercentOfMaxHealthAndHealsAtNight(TestContext context) {
        var player = player(context, 3.5, 1, 3.5);
        var dayTarget = context.spawnMob(EntityType.PIG, 3, 1, 4);
        var nightTarget = context.spawnMob(EntityType.PIG, 3, 1, 5);
        var referenceTarget = context.spawnMob(EntityType.PIG, 4, 1, 4);
        player.setHealth(10);
        var world = context.getWorld();
        long originalTime = world.getTimeOfDay();
        try {
            world.setTimeOfDay(6000);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            player.attack(referenceTarget);
            float referenceDamage = referenceTarget.getMaxHealth() - referenceTarget.getHealth();
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(UniqueWeapons.TWILIGHT_SCYTHE));
            player.attack(dayTarget);
            float daytimeDamage = dayTarget.getMaxHealth() - dayTarget.getHealth();
            context.assertTrue(Math.abs(daytimeDamage - referenceDamage - dayTarget.getMaxHealth() * 0.05F) < 0.001F
                            && player.getHealth() == 10,
                    "Daytime melee must add 5% of the target's max health without lifesteal");
            world.setTimeOfDay(14000);
            player.attack(nightTarget);
            float nightDamage = nightTarget.getMaxHealth() - nightTarget.getHealth();
            context.assertTrue(Math.abs(nightDamage - daytimeDamage) < 0.001F
                            && Math.abs(player.getHealth() - (10 + nightDamage * 0.1F)) < 0.001F,
                    "Night melee must restore 10% of the actual damage dealt");
            context.complete();
        } finally {
            world.setTimeOfDay(originalTime);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_unique_weapons")
    public void scytheAddsPercentDamageToDragonHeadHits(TestContext context) {
        var player = player(context, 3.5, 1, 3.5);
        var reference = EntityType.ENDER_DRAGON.create(context.getWorld());
        var target = EntityType.ENDER_DRAGON.create(context.getWorld());
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        player.attack(reference.head);
        float referenceDamage = reference.getMaxHealth() - reference.getHealth();
        context.assertTrue(referenceDamage > 0, "The dragon's head must accept normal melee damage");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(UniqueWeapons.TWILIGHT_SCYTHE));
        player.attack(target.head);
        float scytheDamage = target.getMaxHealth() - target.getHealth();
        context.assertTrue(Math.abs(scytheDamage - referenceDamage - target.getMaxHealth() * 0.05F) < 0.001F,
                "The scythe must add 5% of the dragon's max health without losing the head hit");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_unique_weapons")
    public void elderKillsAreDistinctAndGroupedByPlayerAndMonument(TestContext context) {
        var data = WeaponAcquisitionData.getOrCreate(context.getWorld());
        UUID player = UUID.randomUUID();
        UUID guardian = UUID.randomUUID();
        var site = new net.minecraft.util.math.ChunkPos(context.getAbsolutePos(net.minecraft.util.math.BlockPos.ORIGIN));
        context.assertEquals(data.recordMonumentKill(player, site.toLong(), guardian), 1, "First guardian");
        context.assertEquals(data.recordMonumentKill(player, site.toLong(), guardian), 1, "Repeat guardian");
        context.assertEquals(data.recordMonumentKill(player, site.toLong() + 1, UUID.randomUUID()), 1, "Other monument");
        context.assertEquals(data.recordMonumentKill(UUID.randomUUID(), site.toLong(), UUID.randomUUID()), 1, "Other player");
        context.assertEquals(data.recordMonumentKill(player, site.toLong(), UUID.randomUUID()), 2, "Second guardian");
        context.assertEquals(data.recordMonumentKill(player, site.toLong(), UUID.randomUUID()), 3, "Third guardian");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "new_unique_weapons")
    public void onlyNewMoonNightKillsCountTowardsScythe(TestContext context) {
        var player = player(context, 3.5, 1, 3.5);
        var victim = context.spawnMob(EntityType.ENDERMAN, 3, 1, 4);
        var world = context.getWorld();
        var unique = WorldUniqueData.getOrCreate(world);
        var originalOwner = unique.getOwner("twilight_scythe");
        long originalTime = world.getTimeOfDay();
        try {
            if (originalOwner != null) {
                unique.releaseId(originalOwner, "twilight_scythe");
            }
            for (int i = 0; i < 19; i++) {
                WeaponAcquisitionData.getOrCreate(world).recordNewMoonKill(player.getUuid());
            }
            world.setTimeOfDay(4 * 24000 + 6000);
            UniqueWeaponTriggers.onKill(player, victim);
            world.setTimeOfDay(14000);
            UniqueWeaponTriggers.onKill(player, victim);
            context.assertTrue(unique.getOwner("twilight_scythe") == null,
                    "Neither new moon daytime nor non-new-moon night qualifies");
            world.setTimeOfDay(4 * 24000 + 14000);
            UniqueWeaponTriggers.onKill(player, victim);
            context.assertEquals(unique.getOwner("twilight_scythe"), player.getUuid(),
                    "The twentieth qualifying kill must give the weapon");
            context.complete();
        } finally {
            world.setTimeOfDay(originalTime);
            unique.releaseId(player.getUuid(), "twilight_scythe");
            if (originalOwner != null) {
                unique.claimId(originalOwner, "twilight_scythe");
            }
        }
    }

    private static ServerPlayerEntity player(TestContext context, double x, double y, double z) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "weapon_test"), SyncedClientOptions.createDefault()) {
            @Override
            protected ItemCooldownManager createCooldownManager() {
                return new ItemCooldownManager();
            }

            @Override
            public boolean giveItemStack(ItemStack stack) {
                return true;
            }

            @Override
            public float getAttackCooldownProgress(float baseTime) {
                return 1.0F;
            }
        };
        Vec3d pos = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        return player;
    }
}
