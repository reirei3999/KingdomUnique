package com.kingdomunique.ability;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** 天候操作：晴れ → 雨 → 雷雨 → 晴れ の順に天候を切り替える。 */
public final class WeatherControlAbility {

    /** 切り替えた天候の継続時間（次に使えるまでの1日）。 */
    public static final int WEATHER_TICKS = 24000;

    public enum Weather {
        CLEAR("晴れ"),
        RAIN("雨"),
        THUNDER("雷雨");

        private final String japanese;

        Weather(String japanese) {
            this.japanese = japanese;
        }

        public String getJapanese() {
            return japanese;
        }
    }

    private WeatherControlAbility() {
    }

    public static Weather current(ServerWorld world) {
        // isRaining() は降雨の強さ（徐々に変化）を見るため、設定値そのものを参照する。
        if (world.getLevelProperties().isThundering()) {
            return Weather.THUNDER;
        }
        return world.getLevelProperties().isRaining() ? Weather.RAIN : Weather.CLEAR;
    }

    public static Weather next(Weather weather) {
        return switch (weather) {
            case CLEAR -> Weather.RAIN;
            case RAIN -> Weather.THUNDER;
            case THUNDER -> Weather.CLEAR;
        };
    }

    public static boolean activate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (world.getRegistryKey() != World.OVERWORLD) {
            player.sendMessage(Text.literal("§c[天候操作] 天候のある地上（オーバーワールド）でのみ使えます。"), true);
            return false;
        }
        Weather target = next(current(world));
        switch (target) {
            case CLEAR -> world.setWeather(WEATHER_TICKS, 0, false, false);
            case RAIN -> world.setWeather(0, WEATHER_TICKS, true, false);
            case THUNDER -> world.setWeather(0, WEATHER_TICKS, true, true);
        }
        world.playSound(null, player.getBlockPos(),
                target == Weather.THUNDER ? SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER : SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.WEATHER, 0.6F, 1.0F);
        player.sendMessage(Text.literal("§9[天候操作] 天候を「" + target.getJapanese() + "」に変えた。"), true);
        return true;
    }
}
