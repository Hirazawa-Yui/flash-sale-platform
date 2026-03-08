package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 300000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final Long CACHE_SHOP_TYPE_TTL = 30L;
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:";

    public static final String FOLLOW_KEY = "follow:";

    public static final String SECKILL_STOCK_KEY = "seckill:product:stock:";
    /**
     * 秒杀已购用户集合（一人一单）。
     * 【注意】seckill.lua 里用字符串字面量 'seckill:product:order:' 重建同一个键名，
     * Lua 引用不到 Java 常量 —— 改这里必须同步改 Lua，反之亦然。
     */
    public static final String SECKILL_ORDER_KEY = "seckill:product:order:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
