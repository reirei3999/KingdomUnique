package com.kingdomunique.kingdom;

import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.entity.ModEntities;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

public final class HeroGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void fallenHeroIsRevivedAtTheAltar(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity king = player(world);
        HeroEntity hero = spawnHero(context, king, HeroClass.ARCHER, new BlockPos(1, 2, 1));
        hero.damage(world.getDamageSources().outOfWorld(), 10000.0F);
        FallenHeroData fallen = FallenHeroData.getOrCreate(world.getServer().getWorld(World.OVERWORLD));
        context.assertTrue(fallen.count(king.getUuid()) == 1, "A slain hero must wait at the altar");

        ItemStack apple = new ItemStack(Items.GOLDEN_APPLE, 2);
        BlockPos altar = context.getAbsolutePos(new BlockPos(3, 2, 3));
        context.assertFalse(KingdomManager.reviveHero(king, altar, apple), "Reviving must cost experience levels");
        king.experienceLevel = KingdomManager.HERO_REVIVE_LEVEL_COST;
        context.assertTrue(KingdomManager.reviveHero(king, altar, apple), "The fallen hero must be revivable");

        HeroEntity revived = world.getEntitiesByClass(HeroEntity.class, new Box(altar).expand(4.0D),
                h -> h.isAlive() && h != hero).stream().findFirst().orElse(null);
        context.assertTrue(revived != null && revived.getHeroClass() == HeroClass.ARCHER,
                "The same hero class must come back");
        context.assertTrue(king.getUuid().equals(revived.getOwnerUuid()), "The revived hero must keep its king");
        context.assertTrue(revived.getHealth() == revived.getMaxHealth(), "The revived hero must be at full health");
        context.assertTrue(apple.getCount() == 1 && king.experienceLevel == 0, "The offering must be consumed");
        context.assertTrue(fallen.count(king.getUuid()) == 0, "A revived hero must leave the fallen list");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void archerShootsArrows(TestContext context) {
        ServerWorld world = context.getWorld();
        HeroEntity archer = spawnHero(context, player(world), HeroClass.ARCHER, new BlockPos(1, 2, 1));
        var zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
        archer.shootAt(zombie, 1.0F);
        context.assertTrue(!world.getEntitiesByClass(ArrowEntity.class, archer.getBoundingBox().expand(3.0D),
                arrow -> arrow.getOwner() == archer).isEmpty(), "The archer must fire an arrow");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void summonHornHasARecipe(TestContext context) {
        context.assertTrue(context.getWorld().getRecipeManager()
                        .get(Identifier.of("kingdomunique", "hero_summon_horn")).isPresent(),
                "The hero summon horn must be craftable");
        context.complete();
    }

    private static HeroEntity spawnHero(TestContext context, ServerPlayerEntity king, HeroClass heroClass, BlockPos pos) {
        HeroEntity hero = ModEntities.HERO.create(context.getWorld());
        BlockPos abs = context.getAbsolutePos(pos);
        hero.refreshPositionAndAngles(abs.getX() + 0.5D, abs.getY(), abs.getZ() + 0.5D, 0.0F, 0.0F);
        hero.setOwnerUuid(king.getUuid());
        hero.setHeroClass(heroClass);
        context.getWorld().spawnEntity(hero);
        return hero;
    }

    private static ServerPlayerEntity player(ServerWorld world) {
        return new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "hero_test"), SyncedClientOptions.createDefault()) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
            }
        };
    }
}
