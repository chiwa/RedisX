package com.zengcode.redisx.annotation.pubsub;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublishX {
    /** logical topic name, e.g. "user-updated" */
    String topic();

    /** optional event name */
    String event() default "";

    /** SpEL for payload; default is method #result */
    String payload() default "#result";

    /** SpEL condition; true → publish, false → skip */
    String condition() default "true";
}