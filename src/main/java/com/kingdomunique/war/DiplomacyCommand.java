package com.kingdomunique.war;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** 外交コマンド（/ku war / ally / peace / diplomacy）。Gemini 提示を既存の /ku 一元登録へ接続。 */
public final class DiplomacyCommand {

    private DiplomacyCommand() {
    }

    public static void registerCommands(LiteralArgumentBuilder<ServerCommandSource> ku) {
        ku.then(CommandManager.literal("war")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> declareWar(ctx.getSource().getPlayerOrThrow(),
                                EntityArgumentType.getPlayer(ctx, "target")))));
        ku.then(CommandManager.literal("ally")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> proposeOrAcceptAlly(ctx.getSource().getPlayerOrThrow(),
                                EntityArgumentType.getPlayer(ctx, "target")))));
        ku.then(CommandManager.literal("peace")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> makePeace(ctx.getSource().getPlayerOrThrow(),
                                EntityArgumentType.getPlayer(ctx, "target")))));
        ku.then(CommandManager.literal("diplomacy")
                .executes(ctx -> showStatus(ctx.getSource().getPlayerOrThrow())));
    }

    private static int declareWar(ServerPlayerEntity sender, ServerPlayerEntity target) {
        if (sender.getUuid().equals(target.getUuid())) {
            sender.sendMessage(Text.literal("§c自国に宣戦布告することはできません。"), false);
            return 0;
        }
        ServerWorld world = sender.getServerWorld();
        KingdomDiplomacyState state = KingdomDiplomacyState.get(world);
        if (state.isAtWar(sender.getUuid(), target.getUuid())) {
            sender.sendMessage(Text.literal("§eすでに " + target.getName().getString() + " と戦争状態です。"), false);
            return 0;
        }
        state.setRelation(sender.getUuid(), target.getUuid(), KingdomDiplomacyState.Relation.WAR);
        state.clearAllyRequest(sender.getUuid());
        state.clearAllyRequest(target.getUuid());
        sender.getServer().getPlayerManager().broadcast(Text.literal(
                "§4§l[宣戦布告] §c" + sender.getName().getString() + " §fの王国が §c"
                        + target.getName().getString() + " §fの王国へ宣戦布告しました！両国の兵士・英雄が交戦状態に入ります！"), false);
        return 1;
    }

    private static int proposeOrAcceptAlly(ServerPlayerEntity sender, ServerPlayerEntity target) {
        if (sender.getUuid().equals(target.getUuid())) {
            sender.sendMessage(Text.literal("§c自分自身と同盟を結ぶことはできません。"), false);
            return 0;
        }
        ServerWorld world = sender.getServerWorld();
        KingdomDiplomacyState state = KingdomDiplomacyState.get(world);
        if (state.isAlly(sender.getUuid(), target.getUuid())) {
            sender.sendMessage(Text.literal("§aすでに " + target.getName().getString() + " と同盟関係にあります。"), false);
            return 0;
        }
        if (state.hasAllyRequestFrom(target.getUuid(), sender.getUuid())) {
            state.clearAllyRequest(target.getUuid());
            state.setRelation(sender.getUuid(), target.getUuid(), KingdomDiplomacyState.Relation.ALLY);
            sender.getServer().getPlayerManager().broadcast(Text.literal(
                    "§b§l[同盟締結] §f" + sender.getName().getString() + " と "
                            + target.getName().getString() + " の王国が同盟を結びました！"), false);
            return 1;
        }
        state.sendAllyRequest(sender.getUuid(), target.getUuid());
        sender.sendMessage(Text.literal("§b" + target.getName().getString() + " へ同盟申請を送りました。"), false);
        target.sendMessage(Text.literal("§b[同盟申請] §f" + sender.getName().getString()
                + " から同盟の申し出が届きました。承諾するには §e/ku ally " + sender.getName().getString() + " §fを実行してください。"), false);
        return 1;
    }

    private static int makePeace(ServerPlayerEntity sender, ServerPlayerEntity target) {
        if (sender.getUuid().equals(target.getUuid())) {
            return 0;
        }
        ServerWorld world = sender.getServerWorld();
        KingdomDiplomacyState state = KingdomDiplomacyState.get(world);
        KingdomDiplomacyState.Relation current = state.getRelation(sender.getUuid(), target.getUuid());
        if (current == KingdomDiplomacyState.Relation.NEUTRAL) {
            sender.sendMessage(Text.literal("§7現在 " + target.getName().getString() + " とは中立関係です。"), false);
            return 0;
        }
        state.setRelation(sender.getUuid(), target.getUuid(), KingdomDiplomacyState.Relation.NEUTRAL);
        sender.getServer().getPlayerManager().broadcast(Text.literal(
                "§e[外交変動] §f" + sender.getName().getString() + " と " + target.getName().getString()
                        + " の関係が『中立（停戦）』になりました。"), false);
        return 1;
    }

    private static int showStatus(ServerPlayerEntity sender) {
        KingdomDiplomacyState state = KingdomDiplomacyState.get(sender.getServerWorld());
        sender.sendMessage(Text.literal("§6=== 現在の外交関係一覧 ==="), false);
        for (ServerPlayerEntity other : sender.getServer().getPlayerManager().getPlayerList()) {
            if (other.getUuid().equals(sender.getUuid())) {
                continue;
            }
            String label = switch (state.getRelation(sender.getUuid(), other.getUuid())) {
                case WAR -> "§c[戦争中]";
                case ALLY -> "§b[同盟]";
                case NEUTRAL -> "§7[中立]";
            };
            sender.sendMessage(Text.literal("・" + other.getName().getString() + " : " + label), false);
        }
        return 1;
    }
}
