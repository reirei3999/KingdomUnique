package com.kingdomunique.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 能力・遺物・呪物の横断的なID管理。 */
public final class ContentRegistry {

    public enum Kind {
        ABILITY("能力"),
        RELIC("遺物"),
        CURSE("呪物");

        private final String japanese;

        Kind(String japanese) {
            this.japanese = japanese;
        }

        public String getJapanese() {
            return japanese;
        }
    }

    private static final Map<String, Kind> KIND_BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> NAME_BY_ID = new LinkedHashMap<>();

    static {
        for (String id : UniqueContentRegistry.ABILITY_IDS) {
            KIND_BY_ID.put(id, Kind.ABILITY);
            UniqueContentRegistry.UniqueEntry entry = UniqueContentRegistry.get(id);
            NAME_BY_ID.put(id, entry == null ? id : entry.displayName());
        }
        for (String id : RelicRegistry.RELIC_IDS) {
            KIND_BY_ID.put(id, Kind.RELIC);
            NAME_BY_ID.put(id, RelicRegistry.getName(id));
        }
        for (String id : CurseRegistry.CURSE_IDS) {
            KIND_BY_ID.put(id, Kind.CURSE);
            NAME_BY_ID.put(id, CurseRegistry.getName(id));
        }
    }

    public static final List<String> ALL_IDS = List.copyOf(KIND_BY_ID.keySet());

    public static final List<String> ABILITY_IDS = UniqueContentRegistry.ABILITY_IDS;
    public static final List<String> RELIC_IDS = RelicRegistry.RELIC_IDS;
    public static final List<String> CURSE_IDS = CurseRegistry.CURSE_IDS;

    private ContentRegistry() {
    }

    public static List<String> idsOf(Kind kind) {
        return switch (kind) {
            case ABILITY -> ABILITY_IDS;
            case RELIC -> RELIC_IDS;
            case CURSE -> CURSE_IDS;
        };
    }

    public static Kind kindOf(String id) {
        return KIND_BY_ID.get(id);
    }

    public static String nameOf(String id) {
        return NAME_BY_ID.getOrDefault(id, id);
    }

    public static List<String> randomDistinct(java.util.Collection<String> candidates, int count) {
        List<String> copy = new ArrayList<>(candidates);
        java.util.Collections.shuffle(copy, java.util.concurrent.ThreadLocalRandom.current());
        return new ArrayList<>(copy.subList(0, Math.min(count, copy.size())));
    }
}
