package com.zengcode.redisx.annotation.pubsub;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SubscribeX {
    /** logical topic name, e.g. "user-updated" */
    String topic();

    /** optional event filter; if set, only accept matching messages */
    String event() default "";
}
