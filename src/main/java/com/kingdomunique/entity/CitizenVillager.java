package com.kingdomunique.entity;

/** 村人に国民情報を付与するためのインターフェース（VillagerEntity に Mixin で実装）。 */
public interface CitizenVillager {

    String ROLE_FARMER = "farmer";
    String ROLE_BUILDER = "builder";
    String ROLE_SMITH = "smith";
    String ROLE_SOLDIER = "soldier";

    String ku$getRole();

    void ku$setRole(String role);

    /** 王国の王UUID（未国民なら空文字）。 */
    String ku$getOwnerUuid();

    void ku$setOwnerUuid(String uuid);
}
