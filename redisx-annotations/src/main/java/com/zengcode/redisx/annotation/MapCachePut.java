package com.zengcode.redisx.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MapCachePut {
    String cacheName();       // ชื่อ Hash
    String key();        // Expression เช่น "#user.id"
    long ttlSeconds() default 0;     // optional TTL
}