package com.zengcode.redisx.autoconfiguration.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zengcode.redisx.autoconfiguration.cache.CacheProps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@AutoConfiguration
@EnableConfigurationProperties({CacheProps.class, PubSubProps.class})
@ConditionalOnClass({RedisMessageListenerContainer.class, StringRedisTemplate.class})
@ConditionalOnProperty(prefix = "redisx.cache", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RedisXPubSubStarterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    @ConditionalOnProperty(prefix = "redisx.pubsub", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory cf) {
        var c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        return c;
    }

    @Bean
    @ConditionalOnMissingBean(PublishXAspect.class)
    @ConditionalOnProperty(prefix = "redisx.pubsub", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public PublishXAspect publishXAspect(StringRedisTemplate srt,
                                         CacheProps cacheProps,
                                         PubSubProps pubProps,
                                         ObjectMapper om,
                                         @Value("${spring.application.name:redisx-app}") String appName) {
        return new PublishXAspect(srt, cacheProps, pubProps, om, appName);
    }

    @Bean
    @ConditionalOnMissingBean(SubscribeXRegistrar.class)
    @ConditionalOnProperty(prefix = "redisx.pubsub", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SubscribeXRegistrar subscribeXRegistrar(RedisMessageListenerContainer container,
                                                   CacheProps cacheProps,
                                                   PubSubProps pubProps,
                                                   ObjectMapper om) {
        return new SubscribeXRegistrar(container, cacheProps, pubProps, om);
    }
}