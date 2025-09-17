package com.zengcode.redisx.autoconfiguration.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zengcode.redisx.annotation.pubsub.PublishX;
import com.zengcode.redisx.autoconfiguration.cache.CacheProps;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

@Aspect
public class PublishXAspect {

    private static final Logger log = LoggerFactory.getLogger(PublishXAspect.class);

    private final StringRedisTemplate srt;
    private final CacheProps cacheProps;
    private final PubSubProps pubProps;
    private final ObjectMapper om;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final String appName;

    public PublishXAspect(StringRedisTemplate srt,
                          CacheProps cacheProps,
                          PubSubProps pubProps,
                          ObjectMapper om,
                          String appName) {
        this.srt = srt;
        this.cacheProps = cacheProps;
        this.pubProps = pubProps;
        this.om = om;
        this.appName = appName;
    }

    @Around("@annotation(pub)")
    public Object around(ProceedingJoinPoint pjp, PublishX pub) throws Throwable {
        Object result = pjp.proceed();
        if (!cacheProps.isEnabled() || !pubProps.isEnabled()) return result;

        EvaluationContext ctx = buildCtx(pjp, result);

        Boolean cond = getBoolean(ctx, pub.condition(), true);
        if (!cond) {
            log.debug("[PublishX] condition=false, skip publish. topic={}, method={}",
                    pub.topic(), methodSig(pjp));
            return result;
        }

        // topic guard
        String topic = buildTopic(pub.topic());
        if (!StringUtils.hasText(topic)) {
            log.warn("[PublishX] empty topic. skip publish. method={}", methodSig(pjp));
            return result;
        }

        // evaluate payload
        Object payload = null;
        try {
            payload = parser.parseExpression(pub.payload()).getValue(ctx);
        } catch (Exception e) {
            log.warn("[PublishX] payload SpEL evaluation error. method={}, expr={}, err={}",
                    methodSig(pjp), pub.payload(), e.toString());
        }

        // build envelope (serialize) – ถ้า fail ให้ log แล้วจบ; ไม่ต้อง retry
        final String envelope;
        try {
            envelope = om.writeValueAsString(new PubSubMessage<>(pub.event(), appName, payload));
        } catch (Exception se) {
            log.error("[PublishX] serialize envelope failed. topic={}, event={}, payloadClass={}, err={}",
                    topic, pub.event(), (payload != null ? payload.getClass().getName() : "null"), se.toString(), se);
            return result;
        }

        int attempts = 0;
        long delay = pubProps.getPublishBackoffMs();
        int max = Math.max(1, pubProps.getPublishMaxAttempts());

        while (true) {
            attempts++;
            try {
                srt.convertAndSend(topic, envelope);

                if (log.isInfoEnabled()) {
                    if (pubProps.isLogPayload()) {
                        log.info("[PublishX] published topic={}, event={}, payload={}",
                                topic, pub.event(), truncate(envelope));
                    } else {
                        log.info("[PublishX] published topic={}, event={}, payloadClass={}",
                                topic, pub.event(), payload != null ? payload.getClass().getName() : "null");
                    }
                }
                break; // success
            } catch (Exception ex) {
                if (attempts >= max) {
                    log.error("[PublishX] FAILED after {} attempts. topic={}, event={}, err={}",
                            attempts, topic, pub.event(), ex.toString(), ex);
                    break;
                } else {
                    log.warn("[PublishX] attempt {}/{} failed. retry in {} ms. topic={}, err={}",
                            attempts, max, delay, topic, ex.toString());
                    sleep(delay);
                    delay = (long) (delay * Math.max(1.0, pubProps.getPublishBackoffMultiplier()));
                }
            }
        }
        return result;
    }

    private EvaluationContext buildCtx(ProceedingJoinPoint pjp, Object result) {
        EvaluationContext ctx = new StandardEvaluationContext();
        Object[] args = pjp.getArgs();
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        if (names != null) {
            for (int i = 0; i < names.length; i++) ctx.setVariable(names[i], args[i]);
        }
        for (int i = 0; i < args.length; i++) ctx.setVariable("p" + i, args[i]);
        ctx.setVariable("result", result);
        return ctx;
    }

    private Boolean getBoolean(EvaluationContext ctx, String expr, boolean dflt) {
        try {
            Boolean v = parser.parseExpression(expr).getValue(ctx, Boolean.class);
            return v == null ? dflt : v;
        } catch (Exception e) {
            log.warn("[PublishX] condition parse error: {} (default={})", e.toString(), dflt);
            return dflt;
        }
    }

    private String buildTopic(String logical) {
        String prefix = cacheProps.getPrefix();
        return (StringUtils.hasText(prefix) ? prefix + ":pub:" + logical : "pub:" + logical);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // restore interrupt flag
        }
    }

    private String methodSig(ProceedingJoinPoint pjp) {
        return pjp.getSignature().toShortString();
    }

    private static String truncate(String s) {
        return (s != null && s.length() > 5000) ? s.substring(0, 5000) + "...(trunc)" : s;
    }
}