package com.yiyundao.compensation.common.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    String key() default "";

    int expireSeconds() default 300;

    String message() default "请勿重复提交";

    boolean tryLock() default false;

    long waitTime() default 1000;
}
