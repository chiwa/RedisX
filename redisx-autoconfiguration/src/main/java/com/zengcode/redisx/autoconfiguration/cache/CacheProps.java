package com.zengcode.redisx.autoconfiguration.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redisx.cache")
public class CacheProps {

    /** prefix ของ key ทั้งหมด เช่น "cache" -> cache:<cacheName>:<key> */
    private String prefix = "cache";

    /** TTL default (วินาที) เมื่อ annotation ไม่กำหนด */
    private long defaultTtlSeconds = 300;

    /** เปิด/ปิดระบบแคชทั้งชุด */
    private boolean enabled = true;

    /** อนุญาต cache ค่าที่เป็น null หรือไม่ */
    private boolean cacheNull = false;

    /** TTL (วินาที) สำหรับค่า null */
    private long nullTtlSeconds = 30;

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(long defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public boolean isCacheNull() {
        return cacheNull;
    }

    public void setCacheNull(boolean cacheNull) {
        this.cacheNull = cacheNull;
    }

    public long getNullTtlSeconds() {
        return nullTtlSeconds;
    }

    public void setNullTtlSeconds(long nullTtlSeconds) {
        this.nullTtlSeconds = nullTtlSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
