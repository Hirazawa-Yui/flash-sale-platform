package com.hmdp.mq;

import com.hmdp.entity.Order;
import com.hmdp.metrics.SeckillMetrics;
import com.hmdp.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消费者 — 监听 RocketMQ 异步处理订单落库
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "seckill-order-topic",
        consumerGroup = "seckill-consumer-group"
)
public class SeckillOrderConsumer implements RocketMQListener<Order> {

    @Autowired
    private IOrderService orderService;
    @Autowired
    private SeckillMetrics metrics;

    @Override
    public void onMessage(Order order) {
        // 统计所有投递（含重投）。放在 try 之外，保证失败的投递也被计入，
        // 这样 consumerAttempt == consumerSuccess + consumerFailure 才能成立。
        metrics.consumerAttempt.increment();

        // run-id 守卫：丢弃上一轮残留在途的消息。
        // 这些消息会对着**刚被重置**的库存执行 stock-1 和 INSERT，
        // 症状和"超卖"一模一样 —— 而"无超卖"正是压测要证明的结论之一。
        if (order.getRunId() == null || order.getRunId().longValue() != metrics.getRunId()) {
            metrics.consumerStale.increment();
            // 正常返回 = ACK 丢弃。绝不 throw，否则会进重试/DLQ。
            return;
        }

        log.info("收到秒杀订单消息, orderId={}", order.getId());
        try {
            orderService.createOrder(order);
            metrics.consumerSuccess.increment();
        } catch (Exception e) {
            metrics.consumerFailure.increment();
            log.error("处理秒杀订单失败, orderId={}", order.getId(), e);
            throw e; // 抛异常触发 RocketMQ 重试
        }
    }
}
