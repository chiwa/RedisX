package com.zengcode.redisx.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheableX {
    String cacheName();
    String key();
    long ttlSeconds() default 300;
    /** SpEL: true → ใช้ cache; false → bypass ทั้งอ่าน/เขียน */
    String condition() default "";
    /** SpEL: true → ไม่เขียน cache หลังได้ #result */
    String unless() default "";
}
