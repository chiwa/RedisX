package com.zengcode.redisx.autoconfiguration.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@EnableConfigurationProperties(CacheProps.class)
public class RedisXCacheStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redisx.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheableXAspect cacheableXAspect(StringRedisTemplate srt, CacheProps props, ObjectMapper om) {
        return new CacheableXAspect(srt, props, om);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redisx.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheEvictXAspect cacheEvictXAspect(StringRedisTemplate srt, CacheProps props) {
        return new CacheEvictXAspect(srt, props);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redisx.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MapCachePutAspect mapCachePutAspect(StringRedisTemplate srt, CacheProps props, ObjectMapper om) {
        return new MapCachePutAspect(srt, props);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redisx.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MapCacheGetAspect mapCacheGetAspect(StringRedisTemplate srt, CacheProps props, ObjectMapper om) {
        return new MapCacheGetAspect(srt, props);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "redisx.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MapCacheEvictAspect mapCacheEvictAspect(StringRedisTemplate srt, CacheProps props) {
        return new MapCacheEvictAspect(srt, props);
    }
}