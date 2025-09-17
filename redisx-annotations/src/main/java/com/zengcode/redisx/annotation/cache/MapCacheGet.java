package com.zengcode.redisx.annotation.cache;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MapCacheGet {
    String cacheName();
    String key();        // Expression สำหรับ field
}
