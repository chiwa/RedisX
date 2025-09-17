package com.zengcode.redisx.autoconfiguration.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zengcode.redisx.annotation.pubsub.SubscribeX;
import com.zengcode.redisx.autoconfiguration.cache.CacheProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubscribeXRegistrar implements BeanPostProcessor, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SubscribeXRegistrar.class);

    private final RedisMessageListenerContainer container;
    private final CacheProps cacheProps;
    private final PubSubProps pubProps;          // <-- ใช้ควบคุม retry/logging/enable
    private final ObjectMapper om;

    private static class Handler {
        Object bean; Method method; String event; Class<?> paramType;
    }

    private final Map<String, List<Handler>> handlers = new ConcurrentHashMap<>();

    public SubscribeXRegistrar(RedisMessageListenerContainer container,
                               CacheProps cacheProps,
                               PubSubProps pubProps,
                               ObjectMapper om) {
        this.container = container;
        this.cacheProps = cacheProps;
        this.pubProps = pubProps;
        this.om = om;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method m : targetClass.getMethods()) {
            SubscribeX sub = m.getAnnotation(SubscribeX.class);
            if (sub == null) continue;

            if (m.getParameterCount() > 1) {
                throw new IllegalStateException("@SubscribeX supports 0 or 1 parameter: " + m);
            }

            String topic = buildTopic(sub.topic());
            if (!StringUtils.hasText(topic)) {
                log.warn("[SubscribeX] empty topic on method={}, skip register", m.toGenericString());
                continue;
            }

            Handler h = new Handler();
            h.bean = bean;
            h.method = m;
            h.event = sub.event();
            h.paramType = (m.getParameterCount() == 1) ? m.getParameters()[0].getType() : null;

            handlers.computeIfAbsent(topic, k -> new ArrayList<>()).add(h);
            log.info("[SubscribeX] registered handler method={} topic={} event={}",
                    m.toGenericString(), topic, (StringUtils.hasText(h.event) ? h.event : "(any)"));
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!cacheProps.isEnabled() || !pubProps.isEnabled()) {
            log.info("[SubscribeX] disabled by properties (cache.enabled={}, pubsub.enabled={}), skip listeners",
                    cacheProps.isEnabled(), pubProps.isEnabled());
            return;
        }
        if (handlers.isEmpty()) return;

        handlers.forEach((topic, hs) -> {
            container.addMessageListener(new MessageListener() {
                @Override public void onMessage(Message message, byte[] pattern) {
                    String json = new String(message.getBody());
                    try {
                        PubSubMessage<?> msg = om.readValue(json, PubSubMessage.class);
                        for (Handler h : hs) {
                            if (StringUtils.hasText(h.event) && !h.event.equals(msg.getEvent())) {
                                continue; // event filter
                            }
                            invokeWithRetry(topic, h, msg, json);
                        }
                    } catch (Exception e) {
                        log.error("[SubscribeX] invalid message. topic={}, err={}, raw={}",
                                topic, e.toString(), pubProps.isLogPayload() ? truncate(json) : "(payload hidden)");
                    }
                }
            }, ChannelTopic.of(topic));
            log.info("[SubscribeX] listening on topic={}", topic);
        });
    }

    private void invokeWithRetry(String topic, Handler h, PubSubMessage<?> msg, String rawJson) {
        int attempts = 0;
        long delay = pubProps.getHandlerBackoffMs();
        int max = Math.max(1, pubProps.getHandlerMaxAttempts());

        while (true) {
            attempts++;
            try {
                if (h.paramType != null) {
                    Object arg = om.convertValue(msg.getPayload(), h.paramType);
                    h.method.invoke(h.bean, arg);
                } else {
                    h.method.invoke(h.bean);
                }

                if (log.isInfoEnabled()) {
                    if (pubProps.isLogPayload()) {
                        log.info("[SubscribeX] handled topic={}, event={}, handler={}, payload={}",
                                topic, msg.getEvent(), h.method.toGenericString(), truncate(rawJson));
                    } else {
                        log.info("[SubscribeX] handled topic={}, event={}, handler={}, payloadClass={}",
                                topic, msg.getEvent(), h.method.toGenericString(),
                                msg.getPayload() != null ? msg.getPayload().getClass().getName() : "null");
                    }
                }
                break; // success
            } catch (Exception ex) {
                if (attempts >= max) {
                    log.error("[SubscribeX] handler FAILED after {} attempts. topic={}, event={}, handler={}, err={}, raw={}",
                            attempts, topic, msg.getEvent(), h.method.toGenericString(), ex.toString(),
                            pubProps.isLogPayload() ? truncate(rawJson) : "(payload hidden)", ex);
                    break;
                } else {
                    log.warn("[SubscribeX] handler attempt {}/{} failed. retry in {} ms. topic={}, event={}, handler={}, err={}",
                            attempts, max, delay, topic, msg.getEvent(), h.method.toGenericString(), ex.toString());
                    sleep(delay);
                    delay = (long) (delay * Math.max(1.0, pubProps.getHandlerBackoffMultiplier()));
                }
            }
        }
    }

    private String buildTopic(String logical) {
        String prefix = cacheProps.getPrefix();
        return (StringUtils.hasText(prefix) ? prefix + ":pub:" + logical : "pub:" + logical);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private static String truncate(String s) {
        return (s != null && s.length() > 5000) ? s.substring(0, 5000) + "...(trunc)" : s;
    }
}