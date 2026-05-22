package com.hmdp.mq;

import com.hmdp.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 超时关单消费者 — 监听延迟消息，关闭超时未支付订单
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "order-timeout-topic",
        consumerGroup = "order-timeout-consumer-group"
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    @Autowired
    private IOrderService orderService;

    @Override
    public void onMessage(String orderIdStr) {
        Long orderId = Long.valueOf(orderIdStr);
        log.info("收到超时关单消息, orderId={}", orderId);
        try {
            orderService.handleTimeoutClose(orderId);
        } catch (Exception e) {
            log.error("超时关单处理失败, orderId={}", orderId, e);
            throw e; // 抛异常触发 RocketMQ 重试
        }
    }
}
