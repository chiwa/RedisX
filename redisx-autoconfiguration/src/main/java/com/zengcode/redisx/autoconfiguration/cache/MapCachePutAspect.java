package com.zengcode.redisx.autoconfiguration.cache;

import com.zengcode.redisx.annotation.cache.MapCachePut;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;

@Aspect
@Component
public class MapCachePutAspect {

    private final StringRedisTemplate srt;
    private final CacheProps props;
    private final ExpressionParser parser = new SpelExpressionParser();

    public MapCachePutAspect(StringRedisTemplate srt, CacheProps props) {
        this.srt = srt;
        this.props = props;
    }

    @Around("@annotation(mapCachePut)")
    public Object around(ProceedingJoinPoint pjp, MapCachePut mapCachePut) throws Throwable {
        // ถ้า system ปิดอยู่ ให้ข้ามแคช
        if (!props.isEnabled()) {
            return pjp.proceed();
        }

        Object result = pjp.proceed();

        String hashKey = buildHashKey(mapCachePut.cacheName());
        String field   = evalKey(pjp, mapCachePut.key());

        // เขียนลง Hash (เก็บเป็น String ตรง ๆ)
        srt.opsForHash().put(hashKey, field, result == null ? "null" : result.toString());

        // ตั้ง TTL ระดับ hash ถ้ามีระบุ หรือใช้ default จาก props ถ้า ttlSeconds = 0
        long ttl = mapCachePut.ttlSeconds() > 0 ? mapCachePut.ttlSeconds() : props.getDefaultTtlSeconds();
        if (ttl > 0) {
            srt.expire(hashKey, Duration.ofSeconds(ttl));
        }

        return result;
    }

    private String buildHashKey(String cacheName) {
        String prefix = props.getPrefix();
        return (StringUtils.hasText(prefix)) ? prefix + ":" + cacheName : cacheName;
    }

    private String evalKey(ProceedingJoinPoint pjp, String expr) {
        // รองรับทั้ง #id และ #p0
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        EvaluationContext ctx = new StandardEvaluationContext();
        Object[] args = pjp.getArgs();
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();

        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        for (int i = 0; i < args.length; i++) {
            ctx.setVariable("p" + i, args[i]);
        }

        return parser.parseExpression(expr).getValue(ctx, String.class);
    }
}