package com.hmdp;

import com.hmdp.entity.SeckillProduct;
import com.hmdp.entity.Shop;
import com.hmdp.service.ISeckillProductService;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmEcommerceApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShopToRedis() throws InterruptedException {
        cacheClient.setWithLogicalExpire("cache:shop:1", shopService.getById(1), 10L, TimeUnit.SECONDS);
    }

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testRedisIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int j = 0; j < 300; j++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - start));
    }

    @Test
    public void testRedisPendingList() {

        String queueName = "stream:test";
        // 1. 从消息队列获取: XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream:order >
        try {
            List<MapRecord<String, Object, Object>> orderList = stringRedisTemplate.opsForStream().read(
                    Consumer.from("g2", "c2"),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(queueName, ReadOffset.lastConsumed())
            );

            // 3. 解析订单信息
            MapRecord<String, Object, Object> record = orderList.get(0);
            System.out.println(record);

            throw new RuntimeException();

            // 5. ACK确认消息
            // stringRedisTemplate.opsForStream().acknowledge(queueName, "g2", record.getId());
        } catch (Exception e) {

            // 获取pending-list
            List<MapRecord<String, Object, Object>> pendingList = stringRedisTemplate.opsForStream().read(
                    Consumer.from("g2", "c2"),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(queueName, ReadOffset.from("0"))
                    // 这里由于Spring Data Redis2.3.10之前的bug，pendinglist读不到kvMap？？？
            );

            MapRecord<String, Object, Object> pending_record = pendingList.get(0);
            System.out.println(pending_record);  // 或者 log.debug

            stringRedisTemplate.opsForStream().acknowledge(queueName, "g2", pending_record.getId());
        }
    }

    /**
     * 秒杀数据预热：将所有秒杀商品的库存从DB加载到Redis。
     * 每次系统启动或秒杀活动初始化时需执行一次。
     */
    @Test
    public void preloadSeckillStock() {
        List<SeckillProduct> list = seckillProductService.list();
        if (list.isEmpty()) {
            System.out.println("⚠ 没有找到秒杀商品，请先执行 hm_ecommerce.sql");
            return;
        }
        for (SeckillProduct sp : list) {
            String stockKey = RedisConstants.SECKILL_STOCK_KEY + sp.getProductId();
            String orderKey = "seckill:product:order:" + sp.getProductId();
            // 设置Redis库存
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(sp.getStock()));
            // 清空已购买用户集合（每次预热视为新一轮秒杀）
            stringRedisTemplate.delete(orderKey);
            System.out.println("✓ 商品 " + sp.getProductId()
                    + " | 库存: " + sp.getStock()
                    + " | 时间: " + sp.getBeginTime() + " ~ " + sp.getEndTime());
        }
        System.out.println("预热完成，共 " + list.size() + " 个秒杀商品");
    }

    @Test
    public void loadDataRedis() {
        // 查询所有店铺数据
        List<Shop> list = shopService.list();
        // 按照店铺类型分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        // 写入Redis: geoadd key x y member
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            List<Shop> shops = entry.getValue();

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
                locations.add(new RedisGeoCommands.GeoLocation<>
                        (shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }

            // 一次性导入避免多次请求Redis
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

}
