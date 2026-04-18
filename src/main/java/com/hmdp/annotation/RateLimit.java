package com.hmdp.annotation;

import java.lang.annotation.*;

/**
 * 多维度滑动窗口限流注解
 * 基于 Redis Sorted Set + Lua 脚本实现
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流维度，默认全局限流 */
    RateLimitMode mode() default RateLimitMode.GLOBAL;

    /** 窗口内最大请求数 */
    int limit() default 10;

    /** 窗口大小（秒） */
    int windowSeconds() default 1;

    /** 自定义key后缀（可选），如 "seckill" */
    String key() default "";
}
