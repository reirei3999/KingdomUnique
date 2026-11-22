package com.kingdomunique.ability;

import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** プレイヤーごとの能力ランタイム状態（サーバー側のみ）。 */
public final class PlayerAbilityState {

    private int miningRange = 1;
    private long lastDamageTimeMillis = System.currentTimeMillis();
    private long spatialLeapReadyAt = 0L;
    private long phaseThroughReadyAt = 0L;
    private long shadowStepReadyAt = 0L;
    private long gravityReadyAt = 0L;
    private boolean shadowDamageBuff = false;
    private UUID gravityTarget = null;
    private LivingEntity gravityHeldEntity = null;
    private long gravityReleaseAt = 0L;

    /** クールタイム厳密化用: 能力ID -> 次回発動可能ティック（world.getTime()基準）。 */
    private final Map<String, Long> readyTicks = new HashMap<>();

    PlayerAbilityState() {
    }

    public int getMiningRange() {
        return miningRange;
    }

    /** 採掘範囲は 1x1 ⇄ 3x3（最大）の2段階。 */
    public void cycleMiningRange() {
        miningRange = miningRange == 1 ? 3 : 1;
    }

    public long getLastDamageTimeMillis() {
        return lastDamageTimeMillis;
    }

    public void markDamaged(long now) {
        lastDamageTimeMillis = now;
    }

    public long getSpatialLeapReadyAt() {
        return spatialLeapReadyAt;
    }

    public void setSpatialLeapReadyAt(long value) {
        spatialLeapReadyAt = value;
    }

    public long getPhaseThroughReadyAt() {
        return phaseThroughReadyAt;
    }

    public void setPhaseThroughReadyAt(long value) {
        phaseThroughReadyAt = value;
    }

    public long getShadowStepReadyAt() {
        return shadowStepReadyAt;
    }

    public void setShadowStepReadyAt(long value) {
        shadowStepReadyAt = value;
    }

    public long getGravityReadyAt() {
        return gravityReadyAt;
    }

    public void setGravityReadyAt(long value) {
        gravityReadyAt = value;
    }

    public boolean isShadowDamageBuff() {
        return shadowDamageBuff;
    }

    public void setShadowDamageBuff(boolean value) {
        shadowDamageBuff = value;
    }

    public UUID getGravityTarget() {
        return gravityTarget;
    }

    public LivingEntity getGravityHeldEntity() {
        return gravityHeldEntity;
    }

    public void setGravityHeldEntity(LivingEntity entity) {
        gravityHeldEntity = entity;
    }

    public void setGravityTarget(UUID value) {
        gravityTarget = value;
    }

    public long getGravityReleaseAt() {
        return gravityReleaseAt;
    }

    public void setGravityReleaseAt(long value) {
        gravityReleaseAt = value;
    }

    /** 指定能力の次回発動可能ティックを設定する。 */
    public void setReadyTick(String abilityId, long tick) {
        readyTicks.put(abilityId, tick);
    }

    /** 指定能力の次回発動可能ティック（未設定なら0）。 */
    public long getReadyTick(String abilityId) {
        return readyTicks.getOrDefault(abilityId, 0L);
    }
}
