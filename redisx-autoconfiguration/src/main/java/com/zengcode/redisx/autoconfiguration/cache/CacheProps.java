package com.zengcode.redisx.autoconfiguration.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redisx.cache")
public class CacheProps {
    /** default "cache" */
    private String prefix = "cache";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
