package com.zengcode.redisx.autoconfiguration.cache;

import com.zengcode.redisx.annotation.cache.CacheEvictX;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Aspect
public class CacheEvictXAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictXAspect.class);

    private final StringRedisTemplate srt;
    private final CacheProps props;

    public CacheEvictXAspect(StringRedisTemplate srt, CacheProps props) {
        this.srt = srt;
        this.props = props;
    }

    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint pjp, CacheEvictX ann) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();

        if (!props.isEnabled()) {
            // ระบบ cache ถูกปิด → ข้ามไปเรียกเมธอดตรง ๆ ไม่มีการลบ
            log.debug("[CacheEvictX] cache disabled → bypass evict, method={}.{}",
                    method.getDeclaringClass().getSimpleName(), method.getName());
            return pjp.proceed();
        }

        // ชื่อ prefix กลุ่ม
        String groupPrefix = props.getPrefix() + ":" + ann.cacheName() + ":";

        if (ann.beforeInvoke()) {
            // ลบก่อน
            evict(ann, method, args, groupPrefix);
            return pjp.proceed();
        } else {
            // ลบหลัง (เฉพาะเมื่อสำเร็จ)
            Object out = pjp.proceed();
            evict(ann, method, args, groupPrefix);
            return out;
        }
    }

    private void evict(CacheEvictX ann, Method method, Object[] args, String groupPrefix) {
        try {
            if (ann.allEntries()) {
                // ลบทั้งกลุ่มด้วย SCAN
                String pattern = groupPrefix + "*";
                long total = scanAndDelete(pattern, props.getScanCount());
                log.info("[CacheEvictX] EVICT-ALL cacheName={} deleted={} pattern={}",
                        ann.cacheName(), total, pattern);
            } else {
                // ลบรายคีย์
                String eval;
                try {
                    eval = Spel.evalStr(ann.key(), method, args);
                } catch (Exception e) {
                    log.warn("[CacheEvictX] key SpEL eval error → skip evict, expr='{}'", ann.key(), e);
                    return;
                }
                String key = groupPrefix + eval;
                Boolean ok = srt.delete(key);
                log.info("[CacheEvictX] EVICT-ONE key={} ok={}", key, ok);
            }
        } catch (Exception e) {
            log.warn("[CacheEvictX] evict error (ignored) cacheName={} allEntries={}",
                    ann.cacheName(), ann.allEntries(), e);
        }
    }

    /**
     * ลบด้วย SCAN (ปลอดภัยกว่า KEYS), ลบเป็น batch ผ่าน srt.delete(Collection)
     * @return จำนวนคีย์ที่ลบได้
     */
    private long scanAndDelete(String matchPattern, int scanCount) {
        List<String> batch = new ArrayList<>(scanCount);
        AtomicLong total = new AtomicLong(0L);

        srt.execute((RedisConnection con) -> {
            var opts = ScanOptions.scanOptions().match(matchPattern).count(scanCount).build();
            try (var cur = con.scan(opts)) {
                while (cur.hasNext()) {
                    String key = new String(cur.next(), StandardCharsets.UTF_8);
                    batch.add(key);
                    if (batch.size() >= scanCount) {
                        total.addAndGet(deleteBatch(batch));
                        batch.clear();
                    }
                }
            }
            return null;
        });

        if (!batch.isEmpty()) {
            total.addAndGet(deleteBatch(batch));
            batch.clear();
        }
        return total.get();
    }

    private long deleteBatch(List<String> keys) {
        Long removed = srt.delete(keys);
        return removed != null ? removed : 0L;
    }
}