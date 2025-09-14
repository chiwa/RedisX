package com.zengcode.redisx.autoconfiguration.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@EnableConfigurationProperties(CacheProps.class)
public class RedisXCacheStarterAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    public CacheableXAspect cacheableXAspect(StringRedisTemplate srt, CacheProps props) {
        return new CacheableXAspect(srt, props);
    }
}