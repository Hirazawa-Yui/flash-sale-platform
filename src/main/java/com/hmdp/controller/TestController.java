package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Order;
import com.hmdp.entity.SeckillProduct;
import com.hmdp.mapper.OrderMapper;
import com.hmdp.metrics.SeckillMetrics;
import com.hmdp.service.ISeckillProductService;
import com.hmdp.service.IOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 压测辅助控制器 — 生成Token / 初始化秒杀数据 / 旧版同步秒杀对比 / 一致性校验
 * 路径 /test/** 已排除登录拦截，需手动解析Token设置UserHolder
 */
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private IOrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private SeckillMetrics metrics;

    /**
     * 生成压测用 Token，返回 CSV 格式（可直接导入 JMeter CSV Data Set Config）
     * 使用 Redis Pipeline 批量写入，避免逐条写入导致的连接超时丢数据问题
     *
     * 【注意】当前压测方案复用 Redis db11 里已有的 2000 个 token，
     * 调用本接口会覆盖 tokens.csv 并往 Redis 灌一批新键，破坏那份对齐关系。
     * 除非确定要换一批 token，否则不要调用。
     *
     * @param start 起始用户ID（含）
     * @param end   结束用户ID（含）
     * @return CSV文本: token,userId
     */
    @GetMapping("/tokens")
    public String generateTokens(@RequestParam(defaultValue = "1") int start,
                                  @RequestParam(defaultValue = "2000") int end) {
        // 1. 先在内存中生成所有 token 数据和 CSV 内容
        StringBuilder sb = new StringBuilder("token,userId\n");
        // 暂存 key → hashData 映射，后续通过 Pipeline 批量写入 Redis
        Map<String, Map<String, String>> batchData = new LinkedHashMap<>();

        for (int i = start; i <= end; i++) {
            String token = UUID.randomUUID().toString();
            String key = RedisConstants.LOGIN_USER_KEY + token;
            Map<String, String> userMap = new HashMap<>();
            userMap.put("id", String.valueOf(i));
            userMap.put("nickName", "user_" + i);
            userMap.put("icon", "");
            batchData.put(key, userMap);
            sb.append(token).append(",").append(i).append("\n");
        }

        // 2. 使用 Pipeline 一次性批量写入 Redis（2000条数据仅1次网络往返）
        //    原逐条写入 4000 次网络调用 → 现在 1 次，彻底解决连接超时导致的丢数据问题
        stringRedisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Map.Entry<String, Map<String, String>> entry : batchData.entrySet()) {
                    byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                    // 将 Hash 的 field-value 转为 byte[] 格式
                    Map<byte[], byte[]> hashBytes = new HashMap<>();
                    for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
                        hashBytes.put(
                                field.getKey().getBytes(StandardCharsets.UTF_8),
                                field.getValue().getBytes(StandardCharsets.UTF_8));
                    }
                    connection.hMSet(keyBytes, hashBytes);
                    connection.expire(keyBytes, TimeUnit.HOURS.toSeconds(24));
                }
                return null;
            }
        });

        return sb.toString();
    }

    /**
     * 【完整重置 + 开启新一轮压测】
     *
     * 顺序本身就是设计，不能随意调换：
     *   1. 先递增 runId —— 从这一刻起上一轮的在途消息可被消费端丢弃。
     *      必须在破坏任何状态**之前**做。
     *   2. 等异步链路排空（尽力而为，不是安全保证 —— 安全由 runId 守卫提供）。
     *   3. 静默窗口：堵住 run-id 检查堵不住的那个竞态 ——
     *      某消费线程已通过检查、事务执行中，其提交会晚于下面的 DELETE。
     *   4. 快照上一轮 —— 顺手返回，忘记读也不丢数据。
     *   5. 删订单 → 重置 DB → 重置 Redis。
     *   6. startRun 清零计数器，并记下 initialStock
     *      （到读取时 Redis 和 DB 库存都已被扣减，基线无法从状态反推）。
     *
     * 【前置条件】只能在 JMeter 停止后调用。runId 守卫只覆盖 MQ，不覆盖 HTTP：
     * 重置期间发起的请求会穿过下面这段非原子的 Redis/MySQL 写序列，落在哪一轮不确定。
     *
     * @param productId  商品id
     * @param stock      本轮初始库存
     * @param waitSeconds 等待排空的上限（send 超时 3s × (1+2 重试) ≈ 9s，故不宜小于 10）
     * @param quietMs    排空后的静默窗口
     */
    @PostMapping("/init-seckill/{productId}")
    public Result initSeckill(@PathVariable Long productId,
                               @RequestParam(defaultValue = "10000") int stock,
                               @RequestParam(defaultValue = "30") int waitSeconds,
                               @RequestParam(defaultValue = "1000") int quietMs) {
        long t0 = System.currentTimeMillis();

        // 1. 取得安全性：此后旧 run 的在途消息会被消费端丢弃
        long prevRun = metrics.bumpRunId();

        // 2. 等排空
        boolean drained = waitForDrain(waitSeconds * 1000L);

        // 3. 静默窗口
        if (quietMs > 0) {
            try {
                Thread.sleep(quietMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 4. 快照上一轮（必须在 DELETE 之前，否则订单数会被抹掉）
        Map<String, Object> previousRun = snapshot(productId);

        // 5. 删除该商品的历史订单 —— 保证每轮从空表开始，跨轮次可比
        int deletedOrders = orderMapper.delete(
                new QueryWrapper<Order>().eq("product_id", productId));

        // 6. 重置 DB 库存 + 秒杀时间窗
        SeckillProduct seckillProduct = seckillProductService.getById(productId);
        if (seckillProduct == null) {
            seckillProduct = new SeckillProduct();
            seckillProduct.setProductId(productId);
            seckillProduct.setStock(stock);
            seckillProduct.setBeginTime(LocalDateTime.now().minusDays(1));  // 已经开始
            seckillProduct.setEndTime(LocalDateTime.now().plusDays(7));    // 7天后结束
            seckillProductService.save(seckillProduct);
        } else {
            seckillProductService.update()
                    .set("stock", stock)
                    .set("begin_time", LocalDateTime.now().minusDays(1))
                    .set("end_time", LocalDateTime.now().plusDays(7))
                    .eq("product_id", productId)
                    .update();
        }

        // 7. 重置 Redis：先删后设（顺序不能反）
        //    【不要删 icr:order:* 键】序列号一旦回退，同一秒内生成的订单ID会与旧行冲突，
        //    save 会全部主键失败。
        stringRedisTemplate.delete(RedisConstants.SECKILL_STOCK_KEY + productId);
        stringRedisTemplate.delete(RedisConstants.SECKILL_ORDER_KEY + productId);
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.SECKILL_STOCK_KEY + productId, String.valueOf(stock));

        // 8. 开启新一轮
        long newRunId = prevRun + 1;
        metrics.startRun(newRunId, productId, stock);

        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("runId", newRunId);
        r.put("productId", productId);
        r.put("initialStock", stock);
        r.put("deletedOrders", deletedOrders);
        r.put("drainedBeforeReset", drained);
        r.put("waitedMs", System.currentTimeMillis() - t0);
        r.put("previousRun", previousRun);
        return Result.ok(r);
    }

    /**
     * 【一致性校验快照】一次调用拿到一份自洽的完整结果。
     *
     * 先阻塞等异步链路排空（默认 5s），再读计数器与状态并算出全部恒等式。
     * 典型用法：JMeter 停止后调一次，把返回的 JSON 整份贴进笔记。
     *
     * 【注意】不能在 JMeter 运行中调用：LongAdder.sum() 跨 cell 非原子，
     * 且延迟采样在运行中读取会拿到零尾导致百分位偏低。
     *
     * 【注意】JacksonConfig 全局把 Long/long 序列化成**带引号的字符串**，
     * 所以计数器在 JSON 里是 "2000" 而不是 2000；延迟百分位是 double，不带引号。
     */
    @GetMapping("/metrics")
    public Result metricsSnapshot(@RequestParam(defaultValue = "1") Long productId,
                                  @RequestParam(defaultValue = "5") int waitSeconds) {
        boolean waitedUntilDrained = waitForDrain(waitSeconds * 1000L);
        Map<String, Object> m = snapshot(productId);
        m.put("waitedUntilDrained", waitedUntilDrained);
        return Result.ok(m);
    }

    /**
     * 清理所有缓存
     */
    @DeleteMapping("/clear-cache")
    public Result clearCache() {
        stringRedisTemplate.delete("cache:shopType:all");
        return Result.ok("缓存已清理");
    }

    /**
     * 【压测对比用】旧版同步秒杀 — Redisson分布式锁 + DB查库存 + 乐观锁扣减
     * 路径已排除登录拦截，手动从header解析Token设置UserHolder
     */
    @PostMapping("/seckill-sync/{productId}")
    public Result seckillSync(@PathVariable Long productId,
                               @RequestHeader(value = "authorization", required = false) String token) {
        // 1. 校验Token
        if (token == null || token.isEmpty()) {
            return Result.fail("请先登录");
        }

        // 2. 从Redis读取用户数据（与LoginInterceptor逻辑一致）
        // 分步计时：让老路径的鉴权耗时也进入 loginRedis 统计，与新路径可比
        long redisT0 = System.nanoTime();
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + token);
        if (userMap.isEmpty()) {
            return Result.fail("Token无效或已过期");
        }

        // 2.1 刷新 token TTL —— 与 LoginInterceptor 对齐。
        //     新路径每次请求都会走这一步（多一次 Redis 往返），而 /test/** 不走拦截器。
        //     补上它，两条路径的鉴权开销才严格可比；否则这份 A/B 数据是要打折的。
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,
                RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        metrics.recordLoginRedis(System.nanoTime() - redisT0);

        // 3. 手动构建UserDTO并设置到ThreadLocal（/test/**不走拦截器，需手动处理）
        UserDTO userDTO = new UserDTO();
        Object idObj = userMap.get("id");
        userDTO.setId(idObj != null ? Long.valueOf(idObj.toString()) : null);
        userDTO.setNickName(userMap.get("nickName") != null
                ? userMap.get("nickName").toString() : "");
        userDTO.setIcon(userMap.get("icon") != null
                ? userMap.get("icon").toString() : "");

        if (userDTO.getId() == null) {
            return Result.fail("Token数据异常");
        }

        UserHolder.saveUser(userDTO);
        try {
            return orderService.seckillProductSync(productId);
        } finally {
            UserHolder.removeUser();
        }
    }

    // ==================== 内部工具 ====================

    /** 轮询等异步链路排空。超时不抛异常 —— 由调用方决定怎么处理不完整的计数。 */
    private boolean waitForDrain(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (metrics.isDrained()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return metrics.isDrained();
    }

    /** 组装一份完整快照：延迟 + 计数器 + 状态 + 恒等式校验。 */
    private Map<String, Object> snapshot(Long productId) {
        Map<String, Object> counters = metrics.counterSnapshot();
        Map<String, Object> state = stateSnapshot(productId);

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("runId", metrics.getRunId());
        m.put("productId", productId);
        m.put("initialStock", metrics.getInitialStock());
        m.put("runMode", runMode());
        m.put("latency", metrics.latencyStats());
        m.put("counters", counters);
        m.put("state", state);
        m.put("checks", buildChecks(state));
        return m;
    }

    /** 现查 Redis 与 MySQL 的状态。不在热路径上，随便查。 */
    private Map<String, Object> stateSnapshot(Long productId) {
        Map<String, Object> s = new LinkedHashMap<String, Object>();

        String redisStockRaw = stringRedisTemplate.opsForValue()
                .get(RedisConstants.SECKILL_STOCK_KEY + productId);
        // key 不存在必须显式暴露：那意味着重置没跑，下面所有数字都无意义。
        // 切到 db11 之后这是高频场景（db11 里没有 seckill:product:stock:*）。
        s.put("redisStockKeyPresent", redisStockRaw != null);
        s.put("redisStock", redisStockRaw == null ? -1L : Long.parseLong(redisStockRaw));

        Long setSize = stringRedisTemplate.opsForSet()
                .size(RedisConstants.SECKILL_ORDER_KEY + productId);
        s.put("redisPurchasedSetSize", setSize == null ? 0L : setSize);

        SeckillProduct p = seckillProductService.getById(productId);
        s.put("dbProductRowPresent", p != null);
        s.put("dbStock", p == null ? -1 : p.getStock());

        s.put("dbOrderCount", orderMapper.countByProductId(productId));
        s.put("dbDistinctUsers", orderMapper.countDistinctUsersByProductId(productId));
        return s;
    }

    /**
     * 把"保证数据一致性"从断言变成一组布尔值。
     *
     * 【关键】校验分三类，不能混着看：
     *   - 与路径无关的（noOversell / noDuplicateBuyer）：两条路径都适用；
     *   - async.* 只在**本轮跑过新路径**时给出。老路径直接扣 DB、不走 MQ，
     *     拿异步链路恒等式去套同步轮次必然"FAIL"，那是无意义的噪声；
     *   - sync.* 同理，只在跑过老路径时给出。
     *
     * runMode 字段说明了本轮实际跑的是哪条路径，看校验前先看它。
     */
    private Map<String, Object> buildChecks(Map<String, Object> state) {
        long initialStock = metrics.getInitialStock();
        long redisStock = (Long) state.get("redisStock");
        boolean stockKeyPresent = (Boolean) state.get("redisStockKeyPresent");
        long redisSetSize = (Long) state.get("redisPurchasedSetSize");
        long dbStock = ((Integer) state.get("dbStock")).longValue();
        long dbOrderCount = (Long) state.get("dbOrderCount");
        long dbDistinctUsers = (Long) state.get("dbDistinctUsers");

        long asyncReq = metrics.asyncRequests.sum();
        long syncReq = metrics.syncRequests.sum();
        long non2xx = metrics.non2xx.sum();

        long luaAccepted = metrics.luaAccepted.sum();
        long sent = metrics.mqSendOk.sum() + metrics.mqSendFail.sum();
        long attempts = metrics.consumerAttempt.sum();
        long consumerSuccess = metrics.consumerSuccess.sum();

        long syncAccepted = metrics.syncAccepted.sum();
        long syncRejected = metrics.syncOutOfStock.sum() + metrics.syncDuplicate.sum()
                + metrics.syncLockFail.sum() + metrics.syncNotInWindow.sum();

        Map<String, Object> c = new LinkedHashMap<String, Object>();

        // ---------- 与路径无关 ----------
        c.put("noOversell", verdict(dbOrderCount <= initialStock,
                "dbOrderCount <= initialStock"));
        c.put("noDuplicateBuyer", verdict(dbOrderCount == dbDistinctUsers,
                "dbOrderCount == dbDistinctUsers"));

        // ---------- 新路径：Redis + Lua + RocketMQ ----------
        if (asyncReq > 0) {
            c.put("async.redisStockAccounting", verdict(
                    stockKeyPresent && redisStock + luaAccepted == initialStock,
                    "redisStock + luaAccepted == initialStock"));
            c.put("async.redisSetAccounting", verdict(
                    redisSetSize == luaAccepted,
                    "scard(purchasedSet) == luaAccepted"));
            c.put("async.mqAccounting", verdict(
                    luaAccepted == sent,
                    "luaAccepted == mqSendOk + mqSendFail"));
            c.put("async.consumerAccounting", verdict(
                    attempts == metrics.concluded(),
                    "consumerAttempt == consumerSuccess + consumerFailure + consumerStale"));
            // 基准只能是 mqSendOk（不是 sent）：发送失败的消息永远不会到达消费者。
            // 再加上 consumerStale —— 被 run-id 守卫丢弃的上一轮消息也会递增
            // consumerAttempt，但它们不来自本轮。
            c.put("async.drained", verdict(
                    attempts == metrics.mqSendOk.sum() + metrics.consumerStale.sum(),
                    "consumerAttempt == mqSendOk + consumerStale"));
            // 单独把发送失败拎出来当一等公民：一条发送失败 = 用户拿到 success:true、
            // Redis 库存已扣、已进已购集合，但订单永不落库。
            // 这是当前"fire-and-forget + 无补偿"设计的真实缺口，绝不能被别的校验掩盖。
            c.put("async.noSendLoss", verdict(
                    metrics.mqSendFail.sum() == 0,
                    "mqSendFail == 0（非 0 = 存在用户被扣了库存却永远拿不到订单）"));
            c.put("async.endToEnd", verdict(dbOrderCount == consumerSuccess,
                    "dbOrderCount == consumerSuccess"));
            c.put("async.dbStockAccounting", verdict(
                    dbStock + consumerSuccess == initialStock
                            && metrics.consumerStockExhausted.sum() == 0,
                    "dbStock + consumerSuccess == initialStock && consumerStockExhausted == 0"));
        }

        // ---------- 老路径：Redisson + DB ----------
        if (syncReq > 0) {
            c.put("sync.requestAccounting", verdict(
                    syncReq == syncAccepted + syncRejected + non2xx,
                    "syncRequests == syncAccepted + syncRejected + non2xx"));
            c.put("sync.endToEnd", verdict(dbOrderCount == syncAccepted,
                    "dbOrderCount == syncAccepted"));
            c.put("sync.dbStockAccounting", verdict(
                    dbStock + syncAccepted == initialStock,
                    "dbStock + syncAccepted == initialStock"));
            if (asyncReq == 0) {
                // 老路径完全不碰 Redis 秒杀键 —— 这本身就是新老方案的关键差异之一
                c.put("sync.redisUntouched", verdict(
                        stockKeyPresent && redisStock == initialStock && redisSetSize == 0,
                        "redisStock == initialStock && scard(purchasedSet) == 0"));
            }
        }
        return c;
    }

    /** 本轮实际跑了哪条路径。看 checks 之前先看这个。 */
    private String runMode() {
        boolean a = metrics.asyncRequests.sum() > 0;
        boolean s = metrics.syncRequests.sum() > 0;
        if (a && s) {
            return "MIXED (两条路径都跑了 —— 路径专属校验不可信，请各跑各的)";
        }
        if (a) {
            return "async";
        }
        if (s) {
            return "sync";
        }
        return "idle (还没有请求)";
    }

    private static String verdict(boolean ok, String identity) {
        return (ok ? "PASS  " : "FAIL  ") + "(" + identity + ")";
    }
}
