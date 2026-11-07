package com.smartmaterials.support;

import java.time.ZoneId;

/** 全局时区：按月统计额度、生成批次号都以该时区为准。 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of(
            System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Shanghai"));

    private AppTime() {
    }
}
