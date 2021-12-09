package com.demo.annotation;

import java.lang.annotation.*;

/**
 * @author wangyan01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BucketLimiter {

    /**
     * 上一次请求时间key
     * last_mill_request_key
     */
    String last_mill_request_key() default "last_mill_request_key";

    /**
     * 初始化令牌桶的容量
     */
    long limit() default 500;

    /**
     * 请求令牌的数量
     * 每次请求消耗令牌的数量
     */
    long permits() default 1;

    /**
     * 令牌注入的速率
     */
    long rate() default 200;


}
