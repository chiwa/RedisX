package com.zengcode.redisx.autoconfiguration.cache;

import com.zengcode.redisx.annotation.cache.MapCacheGet;
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

@Aspect
@Component
public class MapCacheGetAspect {

    private final StringRedisTemplate srt;
    private final CacheProps props;
    private final ExpressionParser parser = new SpelExpressionParser();

    public MapCacheGetAspect(StringRedisTemplate srt, CacheProps props) {
        this.srt = srt;
        this.props = props;
    }

    @Around("@annotation(mapCacheGet)")
    public Object around(ProceedingJoinPoint pjp, MapCacheGet mapCacheGet) throws Throwable {
        // ถ้า system ปิดอยู่ ให้ข้ามแคช
        if (!props.isEnabled()) {
            return pjp.proceed();
        }

        String hashKey = buildHashKey(mapCacheGet.cacheName());
        String field   = evalKey(pjp, mapCacheGet.key());

        Object cached = srt.opsForHash().get(hashKey, field);
        if (cached != null) {
            // เก็บ/อ่านเป็น String ตรง ๆ (ถ้าของจริงเป็น JSON ให้เปลี่ยนตรงนี้เองได้)
            return cached;
        }

        Object result = pjp.proceed();
        if (result != null) {
            srt.opsForHash().put(hashKey, field, result.toString());
            // ไม่ตั้ง TTL ที่นี่ ปล่อยให้ฝั่ง Put หรือ default ของ props จัดการ
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

        // ตั้งชื่อแบบ #id/#name (ต้องใช้ -parameters ถึงจะได้ชื่อจริง)
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        // ตั้งชื่อแบบ #p0/#p1 เสมอ (ไม่ต้องง้อ -parameters)
        for (int i = 0; i < args.length; i++) {
            ctx.setVariable("p" + i, args[i]);
        }

        return parser.parseExpression(expr).getValue(ctx, String.class);
    }
}