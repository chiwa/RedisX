package com.zengcode.redisx.autoconfiguration.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zengcode.redisx.annotation.CacheableX;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Aspect สำหรับ @CacheableX
 * 1) ตรวจว่าเปิดระบบ cache อยู่ไหม + ตรวจ condition (ถ้ามี)
 * 2) สร้าง key
 * 3) ลองอ่านจาก Redis (HIT/MISS)
 * 4) ถ้า MISS เรียกเมธอดจริง แล้วพิจารณา unless + cache-null
 * 5) เขียนกลับ Redis พร้อม TTL
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class CacheableXAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheableXAspect.class);
    private static final String NULL_MARKER = "null"; // marker สำหรับเก็บค่า null ใน cache

    private final StringRedisTemplate redis;
    private final CacheProps props;
    private final ObjectMapper om;

    public CacheableXAspect(StringRedisTemplate redis, CacheProps props, ObjectMapper om) {
        this.redis = redis;
        this.props = props;
        this.om = om;
    }

    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint pjp, CacheableX ann) throws Throwable {
        long startedAt = System.nanoTime();

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();

        // 0) ปิดระบบ cache ทั้งหมด → ทำงานจริงแล้วจบ
        if (!props.isEnabled()) {
            log.debug("[CacheableX] disabled → proceed");
            return proceedAndDebug(pjp, method, startedAt, "disabled");
        }

        // 1) condition: ว่าง = true
        if (!evaluateConditionSafe(ann.condition(), method, args)) {
            log.debug("[CacheableX] condition=false → proceed");
            return proceedAndDebug(pjp, method, startedAt, "condition=false");
        }

        // 2) key & ttl
        String key = buildKeySafe(ann, method, args, startedAt);
        if (key == null) { // กรณี key eval พัง ให้ proceed ต่อ
            return proceedAndDebug(pjp, method, startedAt, "key-error");
        }
        long ttl = resolveTtl(ann);

        // 3) GET from Redis
        String json = getFromRedisSafe(key, startedAt);
        if (json != null) {
            return deserializeOrProceed(json, key, sig, pjp, method, startedAt);
        } else {
            log.debug("[CacheableX] MISS key={}", key);
        }

        // 4) เรียกเมธอดจริง
        Object result = proceedReal(pjp);

        // 5) unless: ว่าง = false
        if (evaluateUnlessSafe(ann.unless(), method, args, result)) {
            log.info("[CacheableX] VETO (unless=true) NOT SET key={}", key);
            return result;
        }

        // 6) SETEX (รองรับ cache-null)
        writeToRedisSafe(key, result, ttl, startedAt);

        return result;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Helpers (อ่านง่าย, แยกเคสชัด)
    // ───────────────────────────────────────────────────────────────────────────

    /** condition: ถ้า expr ว่าง → true, error → true (fail-open) */
    private boolean evaluateConditionSafe(String expr, Method m, Object[] args) {
        if (isBlank(expr)) return true;
        try {
            return Spel.evalBool(expr, m, args, true);
        } catch (Exception e) {
            log.debug("[CacheableX] condition eval error → fallback=true, expr='{}'", expr, e);
            return true;
        }
    }

    /** unless: ถ้า expr ว่าง → false, error → false (fail-closed) */
    private boolean evaluateUnlessSafe(String expr, Method m, Object[] args, Object result) {
        if (isBlank(expr)) return false;
        try {
            return Spel.evalBoolWithResult(expr, m, args, result, false);
        } catch (Exception e) {
            log.debug("[CacheableX] unless eval error → fallback=false, expr='{}'", expr, e);
            return false;
        }
    }

    /** สร้าง key; ถ้า eval ผิดพลาด → log แล้วคืน null เพื่อให้ caller proceed ต่อ */
    private String buildKeySafe(CacheableX ann, Method m, Object[] args, long startedAt) {
        try {
            String evaluated = Spel.evalStr(ann.key(), m, args);
            String key = props.getPrefix() + ":" + ann.cacheName() + ":" + evaluated;
            return key;
        } catch (Exception e) {
            log.debug("[CacheableX] key eval error expr='{}'", ann.key(), e);
            return null;
        }
    }

    /** คืน TTL ที่ใช้จริง: ถ้าใน annotation ไม่กำหนดหรือ <=0 → ใช้ default */
    private long resolveTtl(CacheableX ann) {
        return ann.ttlSeconds() > 0 ? ann.ttlSeconds() : props.getDefaultTtlSeconds();
    }

    /** GET แบบกันพัง: ถ้า Redis ล่ม → log แล้วคืน null (ให้ระบบยังไปต่อได้) */
    private String getFromRedisSafe(String key, long startedAt) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[CacheableX] Redis GET error key={}", key, e);
            return null;
        }
    }

    /** แปลง JSON → object; ถ้าพังให้ proceed จริง (กันเคส schema เปลี่ยน) */
    private Object deserializeOrProceed(
            String json,
            String key,
            MethodSignature sig,
            ProceedingJoinPoint pjp,
            Method method,
            long startedAt
    ) throws Throwable {
        if (NULL_MARKER.equals(json)) {
            long ms = elapsedMs(startedAt);
            log.info("[CacheableX] HIT-NULL key={} took={}ms", key, ms);
            return null;
        }
        try {
            JavaType rt = om.getTypeFactory().constructType(sig.getMethod().getGenericReturnType());
            Object hit = om.readValue(json, rt);
            int size = json.getBytes(StandardCharsets.UTF_8).length;
            log.info("[CacheableX] HIT key={} size={}B", key, size);
            return hit;
        } catch (Exception e) {
            log.warn("[CacheableX] deserialization error → proceed real, key={}", key, e);
            return proceedAndDebug(pjp, method, startedAt, "deserialize-error");
        }
    }

    /** เรียกเมธอดจริง พร้อม debug เวลา */
    private Object proceedAndDebug(ProceedingJoinPoint pjp, Method method, long startedAt, String reason) throws Throwable {
        Object out = proceedReal(pjp);
        long ms = elapsedMs(startedAt);
        log.debug("[CacheableX] PROCEED ({}) {}.{} took={}ms outNull={}",
                reason, method.getDeclaringClass().getSimpleName(), method.getName(), ms, (out == null));
        return out;
    }

    /** เรียกเมธอดจริง (แยกออกมาให้โค้ดอ่านง่าย) */
    private Object proceedReal(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }

    /** SETEX แบบกันพัง: รองรับ cache-null ตาม config */
    private void writeToRedisSafe(String key, Object result, long ttl, long startedAt) {
        try {
            String payload;
            long useTtl;

            if (result == null) {
                if (!props.isCacheNull()) {
                    log.debug("[CacheableX] SKIP SET (result=null) key={}", key);
                    return;
                }
                payload = NULL_MARKER;
                useTtl = props.getNullTtlSeconds();
            } else {
                payload = om.writeValueAsString(result);
                useTtl = ttl;
            }

            redis.opsForValue().set(key, payload, Duration.ofSeconds(useTtl));
            int size = payload.getBytes(StandardCharsets.UTF_8).length;
            long ms = elapsedMs(startedAt);
            log.info("[CacheableX] SET key={} ttl={}s size={}B took={}ms", key, useTtl, size, ms);
        } catch (Exception e) {
            log.warn("[CacheableX] Redis SET error key={}", key, e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static long elapsedMs(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}