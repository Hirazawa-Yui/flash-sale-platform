package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillProduct;
import com.hmdp.entity.Order;
import com.hmdp.mapper.SeckillProductMapper;
import com.hmdp.mapper.OrderMapper;
import com.hmdp.metrics.SeckillMetrics;
import com.hmdp.service.ISeckillProductService;
import com.hmdp.service.IOrderService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 订单 服务实现类
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Autowired
    private IOrderService self;// 注入自身，避免内部调用事务失效
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private SeckillMetrics metrics;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 【不要用 setLocation —— 这是本项目最大的性能陷阱】
        //
        // DefaultRedisScript.getSha1() 的反编译结果是：
        //     monitorenter
        //     ScriptSource.isModified()          ← 文件 stat，I/O
        //     getScriptAsString()                ← 条件满足时重读整个脚本
        //     DigestUtils.sha1DigestAsHex(...)   ← 重算 SHA1
        //     monitorexit
        // 也就是说它**不是**"读一次缓存直接返回"：每次执行 Lua 都要在同一个 synchronized
        // 块里做一次文件检查。用 setLocation 时 scriptSource 是 ClassPathResource，
        // isModified() 会去 stat 文件；在 Spring Boot fat jar 里还要打开嵌套 JarFile，
        // 而 ZipFile.getEntry() 自身是同步的。
        //
        // 后果：几百个并发线程全部在这个监视器上排队做文件 I/O。
        // 实测（400 并发 / 2000 请求）：同一请求内普通 INCR 只要 0.35ms，
        // 而这次 Lua 执行 p50 高达 355ms，差 1000 倍。
        // 它同时压住了新旧两条路径，掩盖了真正想测的 DB 行锁差异。
        //
        // 改成 setScriptText：脚本在类加载时读一次进内存，scriptSource 为 null，
        // isModified() 永远不会被调用，临界区里不再有 I/O。
        // EVALSHA 语义不变（SHA1 仍由 DefaultRedisScript 缓存并在首次执行时下发）。
        SECKILL_SCRIPT.setScriptText(readClasspathText("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /** 启动时一次性读入 classpath 文本资源。 */
    private static String readClasspathText(String name) {
        try (InputStream in = new ClassPathResource(name).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 classpath 文本资源失败: " + name, e);
        }
    }

    // ==================== 已废弃：Redis Stream 消费者 ====================
    // 原方案通过 Redis Stream 做消息队列，后台线程轮询消费。
    // 现替换为 RocketMQ：Producer 见 seckillProduct()，Consumer 见 SeckillOrderConsumer。
    // 保留以下代码供参考，如需回退到 Redis Stream，取消注释即可。

    /* private Runnable createOrder = () -> {
        while (true) {
            String queueName = "stream:order";
            try {
                // 1. 从Redis消息队列获取未处理订单: XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream:order >
                List<MapRecord<String, Object, Object>> orderList = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(queueName, ReadOffset.lastConsumed())
                );

                // 2. 判断是否有消息
                if (orderList == null || orderList.isEmpty()) {
                    // 没有待处理订单，继续
                    continue;
                }

                // 3. 解析订单信息
                MapRecord<String, Object, Object> record = orderList.get(0);
                Map<Object, Object> orderMap = record.getValue();
                Order order = new Order();
                BeanUtil.fillBeanWithMap(orderMap, order, true);

                // 4. 处理订单，保存信息到数据库
                self.createOrder(order);

                // 5. ACK确认消息
                stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

            } catch (Exception e) {
                // 出现消息取出却未被正确消费的情况
                log.error("异步下单错误", e);

                while (true) {
                    try {
                        // 1. 从 pending-list 中取消息处理: XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream:order 0
                        List<MapRecord<String, Object, Object>> pendingList = stringRedisTemplate.opsForStream().read(
                                Consumer.from("g1", "c1"),
                                StreamReadOptions.empty().count(1),
                                StreamOffset.create(queueName, ReadOffset.from("0"))
                        );

                        // 2. 判断是否有消息
                        if (pendingList == null || pendingList.isEmpty()) {
                            // pending-list处理完成，返回读取消息队列
                            break;
                        }

                        // 3. 解析订单信息
                        MapRecord<String, Object, Object> record = pendingList.get(0);
                        Map<Object, Object> orderMap = record.getValue();
                        Order order = new Order();
                        BeanUtil.fillBeanWithMap(orderMap, order, true);

                        // 4. 处理订单，保存信息到数据库
                        self.createOrder(order);

                        // 5. ACK确认消息
                        stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

                    } catch (Exception ex) {
                        log.error("pending-list处理错误", ex);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException exc) {
                            exc.printStackTrace();
                        }
                    }
                }
            }
        }
    }; */

    // 创建线程池执行异步任务（Redis Stream方案，已废弃）
    // private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // @PostConstruct
    // private void init() {
    //     SECKILL_ORDER_EXECUTOR.submit(createOrder);
    // }

    // ==================== 以上为已废弃的 Redis Stream 消费者 ====================

    @Transactional
    public void createOrder(Order order) {
        // 乐观锁扣减库存
        boolean success = seckillProductService.update() // 创建更新构造器
                .setSql("stock = stock -1") // set stock = stock -1
                .eq("product_id", order.getProductId()) // where product_id = ?
                .gt("stock", 0) // where stock > 0
                .update();// 执行更新

        if (!success) {
            // Redis 说还有库存、DB 却说没有 —— 两者已经背离。正常应恒为 0。
            metrics.consumerStockExhausted.increment();
            log.warn("DB 库存不足，丢弃该订单, orderId={}, productId={}",
                    order.getId(), order.getProductId());
            // 【修复】原代码这里只打日志，而 save(order) 在 if 之外**无条件执行**，
            // 于是 tb_order 里会留下"从未扣减过库存"的订单，且不抛异常 → 消息被 ACK 不重试。
            // 那正是"订单落库但库存没扣"的数据不一致，与简历里"保证数据一致性"直接矛盾。
            //
            // 刻意不改成 throw 触发重试：DB 库存真的是 0 时重试必然再失败，
            // 16 次后进 DLQ，纯属浪费。这里记数 + 告警即可。
            return;
        }
        save(order);

        // 发送延迟消息：30分钟后检查订单是否支付，未支付则自动关单
        // RocketMQ 延迟级别 16 = 30分钟
        // rocketMQTemplate.syncSend("order-timeout-topic",
        //         MessageBuilder.withPayload(String.valueOf(order.getId())).build(),
        //         3000, 16);
    }

    /**
     * 支付订单（乐观锁）
     * UPDATE tb_order SET status=2, pay_time=NOW() WHERE id=? AND status=1
     * 只有status=1的订单才能支付，防止与超时关单的并发冲突
     */
    @Override
    public boolean payOrder(Long orderId) {
        return update()
                .set("status", 2)
                .set("pay_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", 1)
                .update();
    }

    /**
     * 超时关单处理（乐观锁 + 回滚库存）
     * UPDATE tb_order SET status=4 WHERE id=? AND status=1
     * 若影响行数为0说明订单已支付/已取消，什么都不做
     */
    @Override
    @Transactional
    public void handleTimeoutClose(Long orderId) {
        // 1. 查询订单
        Order order = getById(orderId);
        if (order == null) {
            log.warn("超时关单: 订单不存在, orderId={}", orderId);
            return;
        }

        // 2. 乐观锁关单（只有status=1未支付的订单才关闭）
        boolean closed = update()
                .set("status", 4)
                .eq("id", orderId)
                .eq("status", 1)
                .update();

        if (!closed) {
            // status≠1，说明订单已支付或被其他操作修改，跳过
            log.info("超时关单: 订单状态已变更，跳过关单, orderId={}", orderId);
            return;
        }

        // 3. 回滚数据库库存
        seckillProductService.update()
                .setSql("stock = stock + 1")
                .eq("product_id", order.getProductId())
                .update();

        // 4. 回滚Redis库存（让Redis库存与DB保持一致）
        stringRedisTemplate.opsForValue().increment("seckill:product:stock:" + order.getProductId());

        // 5. 移除Redis用户购买记录，允许用户再次购买
        stringRedisTemplate.opsForSet().remove("seckill:product:order:" + order.getProductId(),
                order.getUserId().toString());

        log.info("超时关单成功: orderId={}, productId={}, userId={}",
                orderId, order.getProductId(), order.getUserId());
    }

    /**
     * 异步秒杀商品下单
     *
     * @param productId
     * @return
     */
    @Override
    public Result seckillProduct(Long productId) {
        // 这里为了简化，不再判断是否处于秒杀时间

        // 1. 获取当前用户id和订单id
        Long userId = UserHolder.getUser().getId();

        // 分步计时：包住这里面的两次 Redis 往返（INCR 发号 + EVALSHA 执行 Lua）
        long redisT0 = System.nanoTime();

        long orderId = redisIdWorker.nextId("order");
        long nextIdT = System.nanoTime();
        metrics.recordAsyncNextId(nextIdT - redisT0);

        // 1.1 取本轮 runId —— 【必须在 Lua 之前取】
        //     若在 Lua 之后取，重置操作可能与本请求竞态，让这条消息被打上**新一轮**的标记，
        //     消费端的 run-id 守卫就失效了。
        long runId = metrics.getRunId();

        // 2. 执行lua脚本，判断库存和一人一单（Redis原子操作）
        Long success = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                productId.toString(), userId.toString(), String.valueOf(orderId));

        metrics.recordAsyncRedis(System.nanoTime() - nextIdT);

        if (success == 1) {
            metrics.luaOutOfStock.increment();
            return Result.fail("库存不足");
        }
        if (success == 2) {
            metrics.luaDuplicate.increment();
            return Result.fail("您已经购买过该商品");
        }

        // Redis 已提交 INCRBY -1 + SADD。这是下游所有对账恒等式的分母。
        metrics.luaAccepted.increment();

        // 3. 构建订单对象，异步发送到RocketMQ（不等确认，直接返回）
        // 替代原Redis Stream方案：redis.call('xadd','stream:order',...)
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setRunId(runId);

        // 使用异步发送：不阻塞等待Broker确认，吞吐量大幅提升
        // 分步计时：包住 asyncSend 这一句本身。它虽然叫 async，但走的是
        // RocketMQ 内部的异步发送线程池，池子饱和时提交动作本身也会阻塞调用线程。
        long mqT0 = System.nanoTime();
        rocketMQTemplate.asyncSend("seckill-order-topic", order, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                metrics.mqSendOk.increment();
            }
            @Override
            public void onException(Throwable e) {
                // 回调跑在 RocketMQ 生产者线程上，没有请求上下文
                metrics.mqSendFail.increment();
                log.error("RocketMQ异步发送失败, orderId={}", orderId, e);
                // 生产环境此处可落库补偿或告警
            }
        });
        metrics.recordAsyncMq(System.nanoTime() - mqT0);

        // 4. 返回订单id
        return Result.ok(orderId);
    }

    /**
     * 【压测对比用】同步秒杀：Redisson分布式锁 + DB查库存 + 乐观锁扣减
     * 全链路同步阻塞，不做异步解耦，用于与 seckillProduct (Redis+Lua+RocketMQ) 对比QPS
     */
    @Override
    public Result seckillProductSync(Long productId) {
        // 1. 查询秒杀商品信息
        SeckillProduct product = seckillProductService.getById(productId);

        // 2. 判断是否处于秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(product.getBeginTime()) || now.isAfter(product.getEndTime())) {
            metrics.syncNotInWindow.increment();
            return Result.fail("秒杀尚未开始或已结束");
        }

        // 3. 判断库存是否充足
        if (product.getStock() <= 0) {
            metrics.syncOutOfStock.increment();
            return Result.fail("库存不足");
        }

        // 4. 获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 5. Redisson分布式锁（一人一单）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock;
        try {
            isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (!isLock) {
            metrics.syncLockFail.increment();
            return Result.fail("请勿重复下单");
        }

        try {
            // 6. 查DB判断是否已购买
            int count = query()
                    .eq("user_id", userId)
                    .eq("product_id", productId)
                    .count();
            if (count > 0) {
                metrics.syncDuplicate.increment();
                return Result.fail("您已经购买过该商品");
            }

            // 7. 乐观锁扣减库存
            boolean success = seckillProductService.update()
                    .setSql("stock = stock -1")
                    .eq("product_id", productId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                metrics.syncOutOfStock.increment();
                return Result.fail("库存不足");
            }

            // 8. 创建订单保存到数据库
            Order order = new Order();
            order.setId(redisIdWorker.nextId("order"));
            order.setUserId(userId);
            order.setProductId(productId);
            save(order);

            metrics.syncAccepted.increment();
            return Result.ok(order.getId());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询当前用户的订单列表（分页，按创建时间倒序）
     */
    @Override
    public Result queryMyOrders(Integer current, Integer size) {
        Long userId = UserHolder.getUser().getId();
        Page<Order> page = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, size));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    /**
     * 查询订单详情
     */
    @Override
    public Result queryOrderById(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        return Result.ok(order);
    }
}
