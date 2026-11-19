package com.kingdomunique.entity;

/** 英雄NPCの行動モード。 */
public enum HeroMode {
    FOLLOW("随伴モード"),
    DEFEND("防衛モード"),
    WAIT("待機モード");

    private final String displayName;

    HeroMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public HeroMode next(boolean allowFollow) {
        HeroMode[] values = values();
        for (int i = 1; i <= values.length; i++) {
            HeroMode candidate = values[(ordinal() + i) % values.length];
            if (allowFollow || candidate != FOLLOW) {
                return candidate;
            }
        }
        return this;
    }
}
