package com.kingdomunique.ability;

import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.entity.ModEntities;
import com.kingdomunique.item.ModItems;
import com.kingdomunique.kingdom.KingdomData;
import com.kingdomunique.kingdom.KingdomEventHandler;
import com.kingdomunique.kingdom.KingdomManager;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public final class HeroGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "hero")
    public void entityTypeSpawnsWithAttributes(TestContext context) {
        HeroEntity hero = context.spawnEntity(ModEntities.HERO, 1, 2, 1);
        try {
            context.assertEquals(hero.getMaxHealth(), 100.0F, "Hero defaults must be registered");
            for (HeroClass heroClass : HeroClass.values()) {
                hero.setHeroClass(heroClass);
                context.assertEquals(hero.getMaxHealth(), heroClass.getHealth(), "Class health must apply");
                context.assertTrue(hero.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                        == heroClass.getAttackDamage(), "Class attack damage must apply");
            }
            context.complete();
        } finally {
            hero.discard();
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "hero")
    public void hornSummonsOnceWithClassAttributes(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.HERO_SUMMON_HORN));
        try {
            ModItems.HERO_SUMMON_HORN.use(context.getWorld(), player, Hand.MAIN_HAND);
            var heroes = ownedHeroes(context, player);
            context.assertEquals(heroes.size(), 1, "Horn must spawn one owned hero");
            HeroEntity hero = heroes.getFirst();
            context.assertEquals(hero.getMaxHealth(), hero.getHeroClass().getHealth(), "Summoned class attributes must apply");
            ModItems.HERO_SUMMON_HORN.use(context.getWorld(), player, Hand.MAIN_HAND);
            context.assertEquals(ownedHeroes(context, player).size(), 1, "Summon limit must reject another hero");
            context.complete();
        } finally {
            ownedHeroes(context, player).forEach(HeroEntity::discard);
            KingdomData.getOrCreate(context.getWorld().getServer().getWorld(World.OVERWORLD)).remove(player.getUuid());
            context.getWorld().setWeather(0, 0, false, false);
        }
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "hero")
    public void possessionCleanupRestoresGravity(TestContext context) throws Exception {
        ServerPlayerEntity player = player(context);
        context.getWorld().onPlayerConnected(player);
        KingdomManager.togglePossession(player);
        context.assertTrue(KingdomManager.possessedHero(player) == null, "Missing assassin must not start possession");
        HeroEntity hero = context.spawnEntity(ModEntities.HERO, 2, 2, 1);
        hero.setAiDisabled(true);
        hero.setOwnerUuid(player.getUuid());
        hero.setHeroClass(HeroClass.ASSASSIN);
        try {
            for (int exit = 0; exit < 4; exit++) {
                boolean originalNoGravity = exit == 3;
                player.setNoGravity(originalNoGravity);
                KingdomManager.togglePossession(player);
                context.assertTrue(KingdomManager.possessedHero(player) == hero, "Must possess the assassin");
                var tick = KingdomEventHandler.class.getDeclaredMethod("tickPossession", ServerPlayerEntity.class);
                tick.setAccessible(true);
                tick.invoke(null, player);
                context.assertTrue(player.hasNoGravity(), "Possession tick must suspend the player");
                if (exit == 0) {
                    KingdomManager.togglePossession(player);
                } else if (exit == 1) {
                    hero.discard();
                    context.assertTrue(KingdomManager.possessedHero(player) == null, "Removed hero must end possession");
                } else {
                    KingdomManager.clearPossession(player);
                }
                context.assertEquals(player.hasNoGravity(), originalNoGravity, "End must restore prior player gravity");
                context.assertFalse(hero.hasNoGravity(), "Hero gravity must remain enabled");
                context.assertFalse(player.getAbilities().flying, "Possession must not enable flight");
                context.assertFalse(player.hasStatusEffect(StatusEffects.INVISIBILITY), "End must remove possession invisibility");
                context.assertTrue(player.getCameraEntity() == player, "End must restore player camera");
                if (exit == 1) {
                    hero = context.spawnEntity(ModEntities.HERO, 2, 2, 1);
                    hero.setOwnerUuid(player.getUuid());
                    hero.setHeroClass(HeroClass.ASSASSIN);
                }
            }
            context.complete();
        } finally {
            KingdomManager.clearPossession(player);
            hero.discard();
            player.discard();
        }
    }

    private static java.util.List<HeroEntity> ownedHeroes(TestContext context, ServerPlayerEntity player) {
        return context.getWorld().getEntitiesByClass(HeroEntity.class, player.getBoundingBox().expand(8),
                hero -> player.getUuid().equals(hero.getOwnerUuid()));
    }

    private static ServerPlayerEntity player(TestContext context) {
        var world = context.getWorld();
        var profile = new GameProfile(UUID.randomUUID(), "hero_test");
        var client = ConnectedClientData.createDefault(profile, false);
        var player = new ServerPlayerEntity(world.getServer(), world, profile, client.syncedOptions());
        new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), player, client);
        player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(1, 2, 1)), 0, 0);
        return player;
    }
}
