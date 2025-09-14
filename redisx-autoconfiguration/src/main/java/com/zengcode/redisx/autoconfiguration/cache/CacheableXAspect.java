package com.zengcode.redisx.autoconfiguration.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zengcode.redisx.annotation.CacheableX;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Aspect
public class CacheableXAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheableXAspect.class);

    private final StringRedisTemplate srt;
    private final ObjectMapper om = new ObjectMapper();
    private String prefix;

    public CacheableXAspect(StringRedisTemplate srt,CacheProps props) {
        this.srt = srt;
        this.prefix = props.getPrefix();
    }

    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint pjp, CacheableX ann) throws Throwable {
        long t0 = System.nanoTime();

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();

        // 1) condition
        boolean useCache = false;
        try {
            useCache = Spel.evalBool(ann.condition(), method, args, true);
        } catch (Exception e) {
            log.warn("[CacheableX] condition eval error -> fallback=true, method={}.{}, expr='{}'",
                    method.getDeclaringClass().getSimpleName(), method.getName(), ann.condition(), e);
            useCache = true;
        }

        if (!useCache) {
            log.debug("[CacheableX] BYPASS (condition=false) method={}.{}",
                    method.getDeclaringClass().getSimpleName(), method.getName());
            return proceedAndLog(pjp, method, t0, "bypass-condition");
        }

        // 2) build key
        String evaluatedKey;
        try {
            evaluatedKey = Spel.evalStr(ann.key(), method, args);
        } catch (Exception e) {
            log.warn("[CacheableX] key eval error -> BYPASS, method={}.{}, expr='{}'",
                    method.getDeclaringClass().getSimpleName(), method.getName(), ann.key(), e);
            return proceedAndLog(pjp, method, t0, "key-error");
        }
        String key = this.prefix + ":" + ann.cacheName() + ":" + evaluatedKey;

        // 3) GET
        String json = null;
        try {
            json = srt.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[CacheableX] Redis GET error -> BYPASS, key={}", key, e);
            return proceedAndLog(pjp, method, t0, "redis-get-error");
        }

        if (json != null) {
            try {
                var rt = sig.getMethod().getGenericReturnType();
                Object hit = om.readValue(json, om.constructType(rt));
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                log.info("[CacheableX] HIT key={} ttl={}s (approx) took={}ms size={}B",
                        key, // TTL แบบ approx ไม่ได้ read TTL จริง เพื่อลด RTT
                        ann.ttlSeconds(),
                        ms, json.length());
                return hit;
            } catch (Exception e) {
                log.warn("[CacheableX] deserialization error -> proceed real, key={}", key, e);
                // fallthrough to proceed real
            }
        } else {
            log.debug("[CacheableX] MISS key={}", key);
        }

        // 4) proceed real
        Object out = proceedAndTime(pjp);

        // 5) unless
        boolean veto = false;
        try {
            veto = Spel.evalBoolWithResult(ann.unless(), method, args, out, false);
        } catch (Exception e) {
            log.warn("[CacheableX] unless eval error -> fallback=false, key={}", key, e);
            veto = false;
        }

        if (veto) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            log.info("[CacheableX] VETO (unless=true) NOT SET key={} took={}ms", key, ms);
            return out;
        }

        if (out == null) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            log.debug("[CacheableX] SKIP SET (result=null) key={} took={}ms", key, ms);
            return out;
        }

        // 6) SETEX
        try {
            String payload = om.writeValueAsString(out);
            srt.opsForValue().set(key, payload, Duration.ofSeconds(ann.ttlSeconds()));
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            log.info("[CacheableX] SET key={} ttl={}s size={}B took={}ms",
                    key, ann.ttlSeconds(), payload.length(), ms);
        } catch (Exception e) {
            log.warn("[CacheableX] Redis SET error key={}", key, e);
        }

        return out;
    }

    private Object proceedAndLog(ProceedingJoinPoint pjp, Method method, long t0, String reason) throws Throwable {
        Object out = proceedAndTime(pjp);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        log.debug("[CacheableX] PROCEED ({}) method={}.{} took={}ms outNull={}",
                reason, method.getDeclaringClass().getSimpleName(), method.getName(), ms, (out == null));
        return out;
    }

    private Object proceedAndTime(ProceedingJoinPoint pjp) throws Throwable {
        long t = System.nanoTime();
        Object out = pjp.proceed();
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t);
        log.debug("[CacheableX] real-method took={}ms", ms);
        return out;
    }
}