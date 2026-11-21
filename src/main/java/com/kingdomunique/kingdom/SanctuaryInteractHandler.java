package com.kingdomunique.kingdom;

import com.kingdomunique.item.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** 完成済みの英雄召喚祭壇（ロードストーン）を右クリックして英雄を召喚する。 */
public final class SanctuaryInteractHandler {

    private SanctuaryInteractHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND
                    || !(world instanceof ServerWorld serverWorld)) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.LODESTONE)) {
                return ActionResult.PASS;
            }
            SanctuaryData data = SanctuaryData.getOrCreate(serverWorld);
            if (!data.isSanctuary(pos)) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            UUID owner = data.getOwner(pos);
            if (owner == null || !owner.equals(serverPlayer.getUuid())) {
                serverPlayer.sendMessage(Text.literal("§c[祭壇] この王国の王だけが英雄を召喚できます。"), false);
                return ActionResult.SUCCESS;
            }

            ItemStack held = serverPlayer.getStackInHand(hand);
            // 金のリンゴを持っていれば、倒れた英雄を復活させる（召喚とは別枠）。
            if (held.isOf(Items.GOLDEN_APPLE)) {
                KingdomManager.reviveHero(serverPlayer, pos);
                return ActionResult.SUCCESS;
            }
            // 英雄の召喚は、祭壇で角笛を吹いたときだけ行える。
            if (!held.isOf(ModItems.HERO_SUMMON_HORN)) {
                serverPlayer.sendMessage(Text.literal(
                        "§e[祭壇] 英雄を召喚するには「英雄召喚の角笛」を持って右クリックしてください。"
                                + "倒れた英雄の復活は金のリンゴを持って右クリックします。"), false);
                return ActionResult.SUCCESS;
            }

            serverWorld.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
            serverWorld.spawnParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                    40, 0.5, 1.0, 0.5, 0.05);
            if (KingdomManager.summonHero(serverPlayer, pos) && !serverPlayer.isCreative()) {
                // 召喚に成功すると角笛は砕ける。
                held.decrement(1);
                serverPlayer.sendMessage(Text.literal("§6[祭壇] 角笛の音が祭壇に響き、角笛は砕け散った。"), false);
            }
            return ActionResult.SUCCESS;
        });
    }
}
