package com.zengcode.redisx.annotation.cache;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MapCacheEvict {

    /**
     * ชื่อ cache (hash key) เช่น "demo:user"
     */
    String cacheName();

    /**
     * field key (SpEL) → ถ้าใส่ = ลบเฉพาะ field
     * ถ้าไม่ใส่และ allEntries=false → ไม่มี effect
     */
    String key() default "";

    /**
     * ถ้า true = ลบทั้ง group (DEL hash key)
     */
    boolean allEntries() default false;
}