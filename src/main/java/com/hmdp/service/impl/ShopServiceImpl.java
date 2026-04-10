package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 根据商铺id查询商铺信息，并做缓存
     *
     * @param id 商铺id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 仅解决缓存穿透
        // Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
        //         RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 解决缓存穿透以及击穿（互斥锁方式）
        // Shop shop = cacheClient.queryWithPassThroughAndMutex(RedisConstants.CACHE_SHOP_KEY, RedisConstants.LOCK_SHOP_KEY,
        //         id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 解决缓存击穿（逻辑过期方式），不需要考虑缓存穿透，因为缓存不会过期且为自动添加，不从数据库查询
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, RedisConstants.LOCK_SHOP_KEY,
                id, Shop.class, this::getById, 180L, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 保存商铺信息到redis
     *
     */
    // public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
    //     Shop shop = getById(id);
    //
    //     // 模拟耗时操作
    //     // Thread.sleep(200);
    //
    //     RedisData redisData = new RedisData();
    //     redisData.setData(shop);
    //     redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    //
    //     stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    // }
    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // 1. 更新数据库
        updateById(shop);

        // 2. 删除缓存
        // 原代码: stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        // 改为: try-catch 包裹，删除失败时发 RocketMQ 异步重试
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // 删除失败（如Redis短暂不可用），发送MQ消息异步重试
            log.error("缓存删除失败，发送RocketMQ异步重试, key={}", cacheKey, e);
            try {
                rocketMQTemplate.syncSend("cache-invalidation-topic", cacheKey);
            } catch (Exception ex) {
                // MQ也发送失败，记录日志，等待TTL兜底
                log.error("发送缓存失效消息失败，等待TTL兜底, key={}", cacheKey, ex);
            }
        }

        return Result.ok();
    }

    /**
     * 根据地理位置和店铺类型查询店铺
     *
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2. 计算分页参数
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3. 查询Redis，按照距离排序，分页。结果包含 shopId 和 distance
        // georadius key x y 10km withdistance limit end
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key,
                        new Circle(x, y, 5000),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .limit(end));
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        // 4. 跳过前start个结果，解析出 ShopId
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();

        if (content.size() <= start) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(content.size());
        Map<String, Distance> map = new HashMap<>(content.size());
        content.stream().skip(start).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            Distance distance = result.getDistance();
            map.put(shopId, distance);
        });

        // 5. 根据 ShopId 查询 Shop
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops = query()
                .in("id", ids)
                .last("ORDER BY FIELD( id," + idsStr + ")")
                .list();
        for (Shop shop : shops) {
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        }

        // 6. 返回
        return Result.ok(shops);
    }
}
