package com.zengcode.redisx.autoconfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisXAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        var t = new RedisTemplate<String, Object>();
        var str = new StringRedisSerializer();
        var json = new GenericJackson2JsonRedisSerializer();
        t.setConnectionFactory(cf);
        t.setKeySerializer(str);
        t.setValueSerializer(json);
        t.setHashKeySerializer(str);
        t.setHashValueSerializer(json);
        t.afterPropertiesSet();
        return t;
    }
}