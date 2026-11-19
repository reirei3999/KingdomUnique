package com.kingdomunique.command;

import com.kingdomunique.item.ItemMenuHandler;
import com.kingdomunique.selection.SelectionEventHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * 全ての /ku サブコマンドをここで1回だけ登録する。
 *
 * <p>Brigadier は同名のルート子ノードを2回追加すると2回目を無視するため、
 * 各モジュールから個別に {@code dispatcher.register(literal("ku"))} すると
 * 後から登録したサブコマンド（例: select）が一切使えなくなる。</p>
 */
public final class ModCommands {

    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> ku = CommandManager.literal("ku");
            ItemMenuHandler.registerCommands(ku, registryAccess);
            SelectionEventHandler.registerCommands(ku);
            dispatcher.register(ku);
        });
    }
}
