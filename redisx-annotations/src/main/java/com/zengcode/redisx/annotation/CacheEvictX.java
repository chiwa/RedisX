package com.zengcode.redisx.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvictX {
    String cacheName();
    String key() default "";   // ใช้เมื่อ allEntries=false
    boolean allEntries() default false;
    boolean beforeInvoke() default false;
}
