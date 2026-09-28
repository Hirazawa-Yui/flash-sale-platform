package com.hmdp.metrics;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 压测埋点计数器 + 延迟采样。
 *
 * 全部用 LongAdder：高写低读场景下远优于 AtomicLong 的单点 CAS。
 *
 * 【纪律】这里的任何计数器都不得读写 Redis/MySQL —— 否则等于把 I/O 加到被测路径上，
 * 那正是我们要测量的东西。状态（库存/订单数）由 TestController 在读取时现查。
 */
@Component
public class SeckillMetrics {

    /** 每条路径的延迟采样容量。溢出会记入 dropped，不静默丢弃。 */
    private static final int LATENCY_CAPACITY = 200_000;

    // ==================== 请求分母（MetricsInterceptor 递增）====================
    public final LongAdder asyncRequests = new LongAdder();
    public final LongAdder syncRequests = new LongAdder();
    public final LongAdder non2xx = new LongAdder();

    // ==================== 新路径：Redis + Lua + RocketMQ ====================
    public final LongAdder luaOutOfStock = new LongAdder();
    public final LongAdder luaDuplicate = new LongAdder();
    public final LongAdder luaAccepted = new LongAdder();
    /** 回调跑在 RocketMQ 生产者线程上，无请求上下文，必须用 LongAdder。 */
    public final LongAdder mqSendOk = new LongAdder();
    public final LongAdder mqSendFail = new LongAdder();

    // ==================== 新路径：消费端 ====================
    public final LongAdder consumerAttempt = new LongAdder();
    public final LongAdder consumerSuccess = new LongAdder();
    public final LongAdder consumerFailure = new LongAdder();
    /** Redis 说还有库存、DB 却说没有 —— 两者背离的信号。修复 H1 后正常应为 0。 */
    public final LongAdder consumerStockExhausted = new LongAdder();
    /** 上一轮的在途消息，被 run-id 守卫丢弃。 */
    public final LongAdder consumerStale = new LongAdder();

    // ==================== 老路径：Redisson + DB ====================
    public final LongAdder syncAccepted = new LongAdder();
    public final LongAdder syncOutOfStock = new LongAdder();
    public final LongAdder syncDuplicate = new LongAdder();
    public final LongAdder syncLockFail = new LongAdder();
    public final LongAdder syncNotInWindow = new LongAdder();

    // ==================== 延迟 ====================
    private final LatencyRecorder asyncLatency = new LatencyRecorder(LATENCY_CAPACITY);
    private final LatencyRecorder syncLatency = new LatencyRecorder(LATENCY_CAPACITY);

    // ==================== 分步耗时（定位瓶颈用）====================
    // 总耗时拆成这几段之后，"时间花在哪"就不再需要靠猜。
    // 注意它们相加不会等于总耗时：剩下的部分是拦截器之外的分发开销、
    // 参数转换、JSON 序列化等。
    /** seckillProduct 内两次 Redis 往返：INCR(发号) + EVALSHA(Lua) */
    private final LatencyRecorder asyncRedisLatency = new LatencyRecorder(LATENCY_CAPACITY);
    /** 上面那两段各自拆开：INCR 发号单独计时（排查二者谁慢时用） */
    private final LatencyRecorder asyncNextIdLatency = new LatencyRecorder(LATENCY_CAPACITY);
    /** seckillProduct 内 rocketMQTemplate.asyncSend 这一句本身的耗时 */
    private final LatencyRecorder asyncMqLatency = new LatencyRecorder(LATENCY_CAPACITY);
    /** 拦截器里的鉴权 Redis 往返：HGETALL + EXPIRE（两条路径共用） */
    private final LatencyRecorder loginRedisLatency = new LatencyRecorder(LATENCY_CAPACITY);

    public void recordAsyncRedis(long nanos) {
        asyncRedisLatency.record(nanos);
    }

    public void recordAsyncNextId(long nanos) {
        asyncNextIdLatency.record(nanos);
    }

    public void recordAsyncMq(long nanos) {
        asyncMqLatency.record(nanos);
    }

    public void recordLoginRedis(long nanos) {
        loginRedisLatency.record(nanos);
    }

    // ==================== 轮次 ====================
    private final AtomicLong runId = new AtomicLong(0);
    private volatile long currentProductId = -1L;
    /** 本轮初始库存。到读取时 Redis 和 DB 都已被扣减，基线无法从状态反推，必须在这里存下。 */
    private volatile long initialStock = -1L;

    public long getRunId() {
        return runId.get();
    }

    public long getCurrentProductId() {
        return currentProductId;
    }

    public long getInitialStock() {
        return initialStock;
    }

    /** 从这一刻起，带旧 runId 的消息可被丢弃。必须在破坏任何状态**之前**调用。 */
    public long bumpRunId() {
        return runId.incrementAndGet();
    }

    /** 清零所有计数器与延迟采样，开启新一轮。 */
    public void startRun(long newRunId, long productId, long stock) {
        runId.set(newRunId);
        currentProductId = productId;
        initialStock = stock;

        asyncRequests.reset();
        syncRequests.reset();
        non2xx.reset();

        luaOutOfStock.reset();
        luaDuplicate.reset();
        luaAccepted.reset();
        mqSendOk.reset();
        mqSendFail.reset();

        consumerAttempt.reset();
        consumerSuccess.reset();
        consumerFailure.reset();
        consumerStockExhausted.reset();
        consumerStale.reset();

        syncAccepted.reset();
        syncOutOfStock.reset();
        syncDuplicate.reset();
        syncLockFail.reset();
        syncNotInWindow.reset();

        asyncLatency.reset();
        syncLatency.reset();
        asyncRedisLatency.reset();
        asyncNextIdLatency.reset();
        asyncMqLatency.reset();
        loginRedisLatency.reset();
    }

    /** 热路径调用：只做一次采样。 */
    public void recordLatency(boolean async, long nanos) {
        if (async) {
            asyncLatency.record(nanos);
        } else {
            syncLatency.record(nanos);
        }
    }

    /**
     * 异步链路是否已排空 —— 用于重置前的等待和 /test/metrics 的一致性读取。
     *
     * 三个条件缺一不可，最后一条尤其容易漏：
     *   1. 所有被 Lua 接受的消息都已发出（含发送失败的）；
     *   2. 所有已发出的消息都已被投递给消费者；
     *   3. 【】每次投递都已**得出结论**。
     *
     * 第 3 条不是多余的：consumerAttempt 是在 onMessage 开头就递增的，
     * 所以"attempt 追平 sent"只说明消息**开始**被消费。若不等结论，
     * 会读到 consumerAttempt=1 但 consumerSuccess 还是 0 的中间态，
     * 让 consumerAccounting 恒等式假性 FAIL。（实测踩过这个坑。）
     */
    public boolean isDrained() {
        long accepted = luaAccepted.sum();
        long sentOk = mqSendOk.sum();
        // 1. 所有被接受的消息，其发送动作都已得出结论（成功或失败）
        if (sentOk + mqSendFail.sum() < accepted) {
            return false;
        }
        // 2.【只能拿 mqSendOk 做基准】发送**失败**的消息永远不会到达消费者，
        //    用 (mqSendOk + mqSendFail) 做基准的话，只要有一次发送失败就永远等不到排空，
        //    会把等待拖满超时。实测踩过这个坑。
        long attempts = consumerAttempt.sum();
        if (attempts < sentOk) {
            return false;
        }
        // 3. 每次投递都已得出结论（stale 也是一次结论，既不算成功也不算失败）
        return concluded() >= attempts;
    }

    /** 已得出结论的投递数：成功 + 失败 + 被守卫丢弃。 */
    public long concluded() {
        return consumerSuccess.sum() + consumerFailure.sum() + consumerStale.sum();
    }

    /** 延迟百分位。**只能在系统静止时调用**（见 LatencyRecorder#stats）。 */
    public Map<String, Object> latencyStats() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("async", asyncLatency.stats());
        m.put("sync", syncLatency.stats());
        Map<String, Object> parts = new LinkedHashMap<String, Object>();
        parts.put("loginRedis", loginRedisLatency.stats());
        parts.put("asyncNextId", asyncNextIdLatency.stats());
        parts.put("asyncRedis", asyncRedisLatency.stats());
        parts.put("asyncMq", asyncMqLatency.stats());
        m.put("breakdown", parts);
        return m;
    }

    /** 全部计数器的扁平快照，含两个"派生对账"指标。 */
    public Map<String, Object> counterSnapshot() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();

        m.put("asyncRequests", asyncRequests.sum());
        m.put("syncRequests", syncRequests.sum());
        m.put("non2xx", non2xx.sum());

        m.put("luaOutOfStock", luaOutOfStock.sum());
        m.put("luaDuplicate", luaDuplicate.sum());
        m.put("luaAccepted", luaAccepted.sum());
        m.put("mqSendOk", mqSendOk.sum());
        m.put("mqSendFail", mqSendFail.sum());

        m.put("consumerAttempt", consumerAttempt.sum());
        m.put("consumerSuccess", consumerSuccess.sum());
        m.put("consumerFailure", consumerFailure.sum());
        m.put("consumerStockExhausted", consumerStockExhausted.sum());
        m.put("consumerStale", consumerStale.sum());
        m.put("consumerConcluded", concluded());

        m.put("syncAccepted", syncAccepted.sum());
        m.put("syncOutOfStock", syncOutOfStock.sum());
        m.put("syncDuplicate", syncDuplicate.sum());
        m.put("syncLockFail", syncLockFail.sum());
        m.put("syncNotInWindow", syncNotInWindow.sum());

        // 派生：请求没被任何业务分支认领的数量。非 0 = 有路径绕过了埋点，
        // 或 Lua 抛错被 WebExceptionAdvice 吞掉（HTTP 200 但 success=false）。
        // 这是最廉价的埋点自身 bug 探测器。
        m.put("asyncUnaccounted", asyncRequests.sum() - non2xx.sum()
                - (luaOutOfStock.sum() + luaDuplicate.sum() + luaAccepted.sum()));
        m.put("syncUnaccounted", syncRequests.sum() - non2xx.sum()
                - (syncAccepted.sum() + syncOutOfStock.sum() + syncDuplicate.sum()
                   + syncLockFail.sum() + syncNotInWindow.sum()));

        return m;
    }
}
