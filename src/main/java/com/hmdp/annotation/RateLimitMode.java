package com.hmdp.annotation;

/**
 * 限流维度枚举
 */
public enum RateLimitMode {
    /** 全局限流 */
    GLOBAL,
    /** 按IP限流 */
    IP,
    /** 按用户限流 */
    USER
}
