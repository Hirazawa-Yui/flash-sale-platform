package com.hmdp.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 写入缓存, 并设置TTL
     *
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 写入缓存，并设置逻辑过期时间 + 物理TTL兜底
     * 物理TTL = 逻辑过期时间的2倍，作为最终兜底保障
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 同时设物理TTL为逻辑过期时间的2倍：即使逻辑过期机制+MQ重试都失败，TTL到期后缓存自动清除
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData),
                unit.toSeconds(time) * 2, TimeUnit.SECONDS);
    }

    /**
     * 指定key查找缓存，利用缓存空值解决缓存穿透
     *
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 从redis查询商铺缓存
        String key = keyPrefix + id;
        String resultJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存存在，两种情况：1. 缓存命中；2. 空缓存
        // 可以使用 StrUtil.isNotBlank() 综合判断null，空字符串，字符串只包含空格的情况
        if (resultJson != null) {
            // if (resultJson != "") { //这样写，shopJson为 "" 时也会跑进来
            if (!resultJson.isEmpty()) {
                // 缓存命中
                return JSONUtil.toBean(resultJson, type);
            } else {
                // 空缓存
                return null;
            }
        }

        // 3. 缓存不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        // 4. 数据库不存在，返回错误
        if (r == null) {
            // 缓存空值，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "",
                    RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES); // 空缓存TTL固定设为2分钟
            // 返回错误
            return null;
        }

        // 5. 存在，写入redis
        // stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
        this.set(key, r, time, unit);

        // 6. 返回
        return r;
    }


    /**
     * 指定key查询缓存，穿透解决方案（缓存空值）,缓存击穿解决方案（互斥锁）
     *
     */
    public <R, ID> R queryWithPassThroughAndMutex(
            String cacheKeyPrefix, String lockKeyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        // 缓存和锁的key
        String cacheKey = cacheKeyPrefix + id;
        String lockKey = lockKeyPrefix + id;

        while (true) {
            // 1. 从redis查询商铺缓存
            String resultJson = stringRedisTemplate.opsForValue().get(cacheKey);

            // 2. 缓存存在，两种情况：1. 缓存命中；2. 空缓存
            if (resultJson != null) {
                if (!resultJson.isEmpty()) {
                    return JSONUtil.toBean(resultJson, type);
                } else {
                    // 空缓存
                    return null;
                }
            }

            // 3. 缓存不存在，尝试获取锁，重建缓存
            if (tryLock(lockKey)) {
                try {
                    // 3.1 获取锁成功，查询数据库
                    R r = dbFallback.apply(id);

                    // 模拟耗时操作
                    // try {
                    //     Thread.sleep(200);
                    // } catch (InterruptedException e) {
                    //     throw new RuntimeException(e);
                    // }

                    // 3.2 数据库不存在，缓存空值，返回错误
                    if (r == null) {
                        // 缓存空值，防止缓存穿透
                        stringRedisTemplate.opsForValue().set(cacheKey, "",
                                RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return null;
                    }

                    // 3.3 存在，写入redis
                    this.set(cacheKey, r, time, unit);

                    // 3.4 返回
                    return r;
                } finally {
                    // 3.5 释放锁（只在获取锁成功的情况下才能释放锁）
                    unLock(lockKey);
                }

            } else {
                // 获取锁失败，则休眠并重试
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 指定key查找缓存，通过逻辑过期解决缓存击穿。
     * <p>
     * 缓存不存在时降级为互斥锁 + 重试重建，防止并发击穿DB；
     * 数据库返回null时写入空值标记（短逻辑过期时间），防止缓存穿透。
     */
    public <R, ID> R queryWithLogicalExpire(
            String cacheKeyPrefix, String lockKeyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        String cacheKey = cacheKeyPrefix + id;
        String lockKey = lockKeyPrefix + id;

        // ==================== 1. 从redis查询缓存 ====================
        String resultJson = stringRedisTemplate.opsForValue().get(cacheKey);

        // ==================== 2. 缓存完全不存在 ====================
        // 原因可能是：①首次访问；②物理TTL到期被清除。
        // 此时无法依赖旧数据兜底，必须降级为互斥锁 + 重试重建，防止大量请求并发击穿DB。
        if (resultJson == null) {
            while (true) {
                // 2.1 双重检查：可能其他线程已重建完成
                resultJson = stringRedisTemplate.opsForValue().get(cacheKey);
                if (resultJson != null) {
                    break; // 已有缓存，跳出循环继续下面的解析逻辑
                }

                // 2.2 尝试获取互斥锁
                if (tryLock(lockKey)) {
                    try {
                        // 2.3 再次双重检查
                        resultJson = stringRedisTemplate.opsForValue().get(cacheKey);
                        if (resultJson != null) {
                            break;
                        }

                        // 2.4 查询数据库
                        R r1 = dbFallback.apply(id);

                        // 2.5 数据库不存在 → 缓存空值标记（短逻辑过期时间），防穿透
                        if (r1 == null) {
                            RedisData nullData = new RedisData();
                            nullData.setData("");
                            nullData.setExpireTime(LocalDateTime.now().plusSeconds(
                                    TimeUnit.MINUTES.toSeconds(RedisConstants.CACHE_NULL_TTL)));
                            stringRedisTemplate.opsForValue().set(cacheKey,
                                    JSONUtil.toJsonStr(nullData),
                                    RedisConstants.CACHE_NULL_TTL * 2, TimeUnit.MINUTES);
                            return null;
                        }

                        // 2.6 数据库存在 → 写入逻辑过期缓存
                        setWithLogicalExpire(cacheKey, r1, time, unit);
                        return r1;
                    } finally {
                        unLock(lockKey);
                    }
                }

                // 2.7 获取锁失败，休眠后重试（带上限防止死循环）
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        // ==================== 3. 缓存存在，解析 ====================
        RedisData redisData = JSONUtil.toBean(resultJson, RedisData.class);
        Object dataObj = redisData.getData();

        // 3.1 空值标记（防穿透）：data 为空字符串
        if (dataObj == null || (dataObj instanceof CharSequence && ((CharSequence) dataObj).length() == 0)) {
            return null;
        }

        // 3.2 正常数据解析：Hutool 将 Object 默认解析为 JSONObject
        JSONObject data = (JSONObject) dataObj;
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // ==================== 4. 缓存未逻辑过期，直接返回 ====================
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // ==================== 5. 缓存已逻辑过期，异步重建 ====================
        // 只有获取锁成功的线程负责重建，其他线程直接返回旧数据
        if (tryLock(lockKey)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);

                    if (r1 == null) {
                        // 数据已被删除 → 写入空值标记防穿透
                        RedisData nullData = new RedisData();
                        nullData.setData("");
                        nullData.setExpireTime(LocalDateTime.now().plusSeconds(
                                TimeUnit.MINUTES.toSeconds(RedisConstants.CACHE_NULL_TTL)));
                        stringRedisTemplate.opsForValue().set(cacheKey,
                                JSONUtil.toJsonStr(nullData),
                                RedisConstants.CACHE_NULL_TTL * 2, TimeUnit.MINUTES);
                    } else {
                        setWithLogicalExpire(cacheKey, r1, time, unit);
                    }
                } catch (Exception e) {
                    log.error("逻辑过期异步重建失败, key={}", cacheKey, e);
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }

        // ==================== 6. 返回旧数据兜底 ====================
        return r;
    }

    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
