package com.zengcode.redisx.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MapCacheGet {
    String name();
    String key();
}
