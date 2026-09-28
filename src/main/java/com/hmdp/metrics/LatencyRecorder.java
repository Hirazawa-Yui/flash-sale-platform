package com.hmdp.metrics;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单条请求路径的延迟采样器 —— 压测用，不是生产监控组件。
 *
 * 只保存原始纳秒样本，读取时才排序算百分位。刻意不引入 HdrHistogram：
 * 本场景是"跑完一轮读一次"，不是持续监控，用不上它的 per-thread 直方图 + 间隔合并那一套。
 */
public class LatencyRecorder {

    /**
     * 原始样本（纳秒）。
     *
     * 用 AtomicLongArray 而不是 long[] + AtomicLong 游标：AtomicLong.getAndIncrement()
     * 只对它**之前**的写建立 happens-before，读线程拿到游标值后并不保证能看到对应槽位的
     * 普通数组写。AtomicLongArray.set() 是 volatile 写、无 CAS 争用，读端可见性有保证。
     */
    private final AtomicLongArray samples;

    private final AtomicLong cursor = new AtomicLong();
    private final LongAdder recorded = new LongAdder();
    private final LongAdder dropped = new LongAdder();

    public LatencyRecorder(int capacity) {
        this.samples = new AtomicLongArray(capacity);
    }

    /** 热路径：两次原子操作，无锁、无分配、无日志。 */
    public void record(long nanos) {
        long i = cursor.getAndIncrement();
        if (i < samples.length()) {
            samples.set((int) i, nanos);
            recorded.increment();
        } else {
            // 溢出必须计数，绝不静默丢弃 —— 静默丢弃会让百分位悄悄失真
            dropped.increment();
        }
    }

    public void reset() {
        cursor.set(0);
        recorded.reset();
        dropped.reset();
    }

    /**
     * 计算百分位。
     *
     * 【前提】只能在系统静止时调用。运行中调用时 cursor 可能已越过尚未写入的槽位，
     * 排序会把一批 0 值算进来，导致百分位系统性偏低。
     */
    public Map<String, Object> stats() {
        int n = (int) Math.min(recorded.sum(), samples.length());
        long[] a = new long[n];
        for (int i = 0; i < n; i++) {
            a[i] = samples.get(i);
        }
        Arrays.sort(a);

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("count", n);
        m.put("p50Ms", pct(a, 0.50));
        m.put("p95Ms", pct(a, 0.95));
        m.put("p99Ms", pct(a, 0.99));
        m.put("maxMs", n == 0 ? 0.0 : a[n - 1] / 1e6);
        m.put("dropped", dropped.sum());
        return m;
    }

    /** 最近秩法（nearest-rank）取百分位。 */
    private static double pct(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int idx = (int) Math.ceil(p * sorted.length) - 1;
        if (idx < 0) {
            idx = 0;
        } else if (idx >= sorted.length) {
            idx = sorted.length - 1;
        }
        return sorted[idx] / 1e6;
    }
}
