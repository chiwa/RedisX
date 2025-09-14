package com.zengcode.redisx.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MapCachePut {
    String name();
    String key();
    /** SpEL ที่คืนค่า object/Map → จะถูก serialize เป็น JSON */
    String value();
    long ttlSeconds() default 0; // 0 = ไม่ตั้ง TTL ให้ Hash key
}