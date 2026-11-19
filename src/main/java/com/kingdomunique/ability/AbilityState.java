package com.kingdomunique.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** サーバー側のプレイヤー能力状態レジストリ。 */
public final class AbilityState {

    private static final Map<UUID, PlayerAbilityState> STATES = new ConcurrentHashMap<>();

    private AbilityState() {
    }

    public static PlayerAbilityState get(UUID uuid) {
        return STATES.computeIfAbsent(uuid, ignored -> new PlayerAbilityState());
    }

    public static void remove(UUID uuid) {
        STATES.remove(uuid);
    }

    public static Map<UUID, PlayerAbilityState> all() {
        return STATES;
    }
}
