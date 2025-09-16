package com.zengcode.redisx.autoconfiguration.cache;

import com.zengcode.redisx.annotation.MapCacheEvict;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
public class MapCacheEvictAspect {

    private final StringRedisTemplate srt;
    private final CacheProps props;
    private final ExpressionParser parser = new SpelExpressionParser();

    public MapCacheEvictAspect(StringRedisTemplate srt, CacheProps props) {
        this.srt = srt;
        this.props = props;
    }

    @AfterReturning("@annotation(evict)")
    public void after(JoinPoint jp, MapCacheEvict evict) {
        // ถ้าปิดระบบ cache → ไม่ทำอะไร
        if (!props.isEnabled()) return;

        String hashKey = buildHashKey(evict.cacheName());

        if (evict.allEntries()) {
            // ลบทั้งกลุ่ม
            srt.delete(hashKey);
            return;
        }

        String keyExpr = evict.key();
        if (StringUtils.hasText(keyExpr)) {
            String field = evalKey(jp, keyExpr);
            if (field != null) {
                srt.opsForHash().delete(hashKey, field);
            }
        }
    }

    private String buildHashKey(String cacheName) {
        String prefix = props.getPrefix();
        return (StringUtils.hasText(prefix)) ? prefix + ":" + cacheName : cacheName;
    }

    private String evalKey(JoinPoint jp, String expr) {
        EvaluationContext ctx = new StandardEvaluationContext();
        Object[] args = jp.getArgs();
        String[] names = ((MethodSignature) jp.getSignature()).getParameterNames();

        // รองรับ #id/#name (ต้องมี -parameters)
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        // รองรับ #p0/#p1 เสมอ (ไม่ต้องง้อ -parameters)
        for (int i = 0; i < args.length; i++) {
            ctx.setVariable("p" + i, args[i]);
        }
        return parser.parseExpression(expr).getValue(ctx, String.class);
    }
}