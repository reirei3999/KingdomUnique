package com.kingdomunique.building;

import com.kingdomunique.entity.HeroClass;
import com.kingdomunique.entity.HeroEntity;
import com.kingdomunique.kingdom.KingdomManager;
import com.kingdomunique.kingdom.PolicyManager;
import com.kingdomunique.war.DiplomacyData;
import com.kingdomunique.war.Warfare;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/** 国境監視塔の侵入検知と、国の門のスマート開閉。 */
public final class DefenseSystem {

    private static final double WATCHTOWER_BASE_RANGE = 16.0D;
    private static final double GATE_RANGE = 5.0D;

    private DefenseSystem() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DefenseSystem::onTick);
    }

    private static long tick = 0L;

    private static void onTick(MinecraftServer server) {
        if (++tick % 5L != 0L) {
            return;
        }
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            return;
        }
        DefenseData data = DefenseData.getOrCreate(world);
        DiplomacyData diplomacy = DiplomacyData.getOrCreate(world);

        for (DefenseData.DefenseSite tower : new java.util.ArrayList<>(data.watchtowers())) {
            tickWatchtower(world, tower, diplomacy);
        }
        for (DefenseData.DefenseSite gate : new java.util.ArrayList<>(data.gates())) {
            tickGate(world, gate, diplomacy);
        }
    }

    /** 監視塔：領土内の敵対プレイヤー／刺客英雄を検知して警告＋発光。 */
    private static void tickWatchtower(ServerWorld world, DefenseData.DefenseSite tower,
                                       DiplomacyData diplomacy) {
        ServerPlayerEntity king = world.getServer().getPlayerManager()
                .getPlayer(tower.owner());
        if (king == null) {
            return;
        }
        double range = WATCHTOWER_BASE_RANGE
                * PolicyManager.watchtowerRangeMultiplier(world, tower.owner());
        BlockPos center = tower.pos();
        boolean warned = false;

        for (PlayerEntity player : world.getPlayers()) {
            if (player.getUuid().equals(tower.owner())) {
                continue;
            }
            if (diplomacy.isAllied(tower.owner(), player.getUuid())) {
                continue;
            }
            if (center.getSquaredDistance(player.getX(), player.getY(), player.getZ())
                    > range * range) {
                continue;
            }
            // 憑依中の刺客英雄がスニークしていれば検知を回避できる。
            HeroEntity possessed = player instanceof ServerPlayerEntity sp
                    ? KingdomManager.possessedHero(sp) : null;
            if (possessed != null && possessed.getHeroClass() == HeroClass.ASSASSIN
                    && possessed.isSneaking()) {
                continue;
            }
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.GLOWING, 100, 0, true, true));
            warned = true;
        }

        for (HeroEntity hero : world.getEntitiesByClass(HeroEntity.class,
                new net.minecraft.util.math.Box(center).expand(range),
                h -> h.isAlive() && h.getHeroClass() == HeroClass.ASSASSIN)) {
            UUID owner = hero.getOwnerUuid();
            if (owner == null || owner.equals(tower.owner())
                    || diplomacy.isAllied(tower.owner(), owner) || hero.isSneaking()) {
                continue;
            }
            hero.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.GLOWING, 100, 0, true, true));
            warned = true;
        }

        if (warned) {
            king.sendMessage(Text.literal(
                    "§c【警告】国境監視塔が領土内への侵入者を感知しました！"), true);
        }
    }

    /** 国の門：王・国民・英雄・同盟プレイヤーが近づけば開き、離れれば閉じる。 */
    private static void tickGate(ServerWorld world, DefenseData.DefenseSite gate,
                                 DiplomacyData diplomacy) {
        BlockPos center = gate.pos();
        boolean friendlyNearby = false;
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(center).expand(GATE_RANGE);

        for (Entity entity : world.getEntitiesByClass(Entity.class, box, e -> e.isAlive())) {
            UUID owner = Warfare.ownerOf(entity);
            if (owner == null) {
                continue;
            }
            if (owner.equals(gate.owner()) || diplomacy.isAllied(gate.owner(), owner)) {
                friendlyNearby = true;
                break;
            }
        }

        if (friendlyNearby == gate.open()) {
            return;
        }
        DefenseData data = DefenseData.getOrCreate(world);
        data.setGateOpen(center, friendlyNearby);
        setLeaves(world, center, friendlyNearby ? Blocks.AIR.getDefaultState()
                : Blocks.IRON_BLOCK.getDefaultState());
    }

    private static void setLeaves(ServerWorld world, BlockPos center, net.minecraft.block.BlockState state) {
        for (int dx = -StructurePlacer.GATE_LEAF_HALF_WIDTH;
             dx <= StructurePlacer.GATE_LEAF_HALF_WIDTH; dx++) {
            for (int y = 0; y < StructurePlacer.GATE_LEAF_HEIGHT; y++) {
                world.setBlockState(center.add(dx, y, 0), state);
            }
        }
    }
}
