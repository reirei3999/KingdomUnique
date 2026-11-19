package com.kingdomunique.kingdom;

/** 王国の政策（Civ6風・7日ごとに1回だけ変更できる）。 */
public enum Policy {

    RICH_STRONG("富国強兵令", "兵士の攻撃力+20% / 農民の収穫効率-20%"),
    INDUSTRY("殖産興業令", "建築速度と作物成長+30% / 兵士の攻撃力-20%"),
    BORDER_GUARD("国境警備令", "領内侵入者に鈍化I / 監視塔の感知範囲1.5倍");

    private final String displayName;
    private final String description;

    Policy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String commandName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Policy fromString(String value) {
        if (value == null) {
            return null;
        }
        for (Policy policy : values()) {
            if (policy.name().equalsIgnoreCase(value) || policy.commandName().equalsIgnoreCase(value)) {
                return policy;
            }
        }
        return null;
    }
}
