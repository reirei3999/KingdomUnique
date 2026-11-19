package com.kingdomunique.entity;

/** 英雄NPCの4クラス（魔法なし・物理特化）。 */
public enum HeroClass {
    ASSASSIN("シャドウ（刺客）", 120.0F, 14.0F, 0.34F, 2.0D, 6.0D),
    KNIGHT("アーサー（近衛騎士団長）", 220.0F, 10.0F, 0.22F, 14.0D, 4.0D),
    ARCHER("ロビン（神弓の狩人）", 100.0F, 9.0F, 0.25F, 4.0D, 16.0D),
    SMITH("ヘファイストス（伝説の鍛冶師）", 160.0F, 6.0F, 0.20F, 8.0D, 8.0D);

    private final String displayName;
    private final float health;
    private final float attackDamage;
    private final float speed;
    private final double armor;
    private final double followRange;

    HeroClass(String displayName, float health, float attackDamage, float speed, double armor, double followRange) {
        this.displayName = displayName;
        this.health = health;
        this.attackDamage = attackDamage;
        this.speed = speed;
        this.armor = armor;
        this.followRange = followRange;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getHealth() {
        return health;
    }

    public float getAttackDamage() {
        return attackDamage;
    }

    public float getSpeed() {
        return speed;
    }

    public double getArmor() {
        return armor;
    }

    public double getFollowRange() {
        return followRange;
    }
}
