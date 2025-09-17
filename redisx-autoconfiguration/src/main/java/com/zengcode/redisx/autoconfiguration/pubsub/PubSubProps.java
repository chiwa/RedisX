package com.zengcode.redisx.autoconfiguration.pubsub;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redisx.pubsub")
public class PubSubProps {
    /** เปิด/ปิดฟีเจอร์ Pub/Sub ทั้งชุด */
    private boolean enabled = true;

    /** retry สำหรับ publish */
    private int publishMaxAttempts = 3;
    private long publishBackoffMs = 100; // delay เริ่มต้น
    private double publishBackoffMultiplier = 2.0;

    /** retry สำหรับ subscriber handler */
    private int handlerMaxAttempts = 3;
    private long handlerBackoffMs = 100;
    private double handlerBackoffMultiplier = 2.0;

    /** log payload เต็ม ๆ หรือไม่ (ป้องกัน log noise/PII) */
    private boolean logPayload = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPublishMaxAttempts() {
        return publishMaxAttempts;
    }

    public void setPublishMaxAttempts(int publishMaxAttempts) {
        this.publishMaxAttempts = publishMaxAttempts;
    }

    public long getPublishBackoffMs() {
        return publishBackoffMs;
    }

    public void setPublishBackoffMs(long publishBackoffMs) {
        this.publishBackoffMs = publishBackoffMs;
    }

    public double getPublishBackoffMultiplier() {
        return publishBackoffMultiplier;
    }

    public void setPublishBackoffMultiplier(double publishBackoffMultiplier) {
        this.publishBackoffMultiplier = publishBackoffMultiplier;
    }

    public int getHandlerMaxAttempts() {
        return handlerMaxAttempts;
    }

    public void setHandlerMaxAttempts(int handlerMaxAttempts) {
        this.handlerMaxAttempts = handlerMaxAttempts;
    }

    public long getHandlerBackoffMs() {
        return handlerBackoffMs;
    }

    public void setHandlerBackoffMs(long handlerBackoffMs) {
        this.handlerBackoffMs = handlerBackoffMs;
    }

    public double getHandlerBackoffMultiplier() {
        return handlerBackoffMultiplier;
    }

    public void setHandlerBackoffMultiplier(double handlerBackoffMultiplier) {
        this.handlerBackoffMultiplier = handlerBackoffMultiplier;
    }

    public boolean isLogPayload() {
        return logPayload;
    }

    public void setLogPayload(boolean logPayload) {
        this.logPayload = logPayload;
    }
}