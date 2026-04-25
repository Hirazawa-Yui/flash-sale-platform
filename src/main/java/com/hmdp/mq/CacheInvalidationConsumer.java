package com.hmdp.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 缓存失效消费者 — 缓存删除失败时通过 RocketMQ 异步重试
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "cache-invalidation-topic",
        consumerGroup = "cache-invalidation-consumer-group"
)
public class CacheInvalidationConsumer implements RocketMQListener<String> {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(String cacheKey) {
        log.info("收到缓存删除重试消息, key={}", cacheKey);
        try {
            Boolean deleted = stringRedisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("缓存删除成功, key={}", cacheKey);
            } else {
                // key 不存在（可能已过期），无需重试
                log.info("缓存key不存在(可能已过期), key={}", cacheKey);
            }
        } catch (Exception e) {
            log.error("缓存删除失败，等待MQ重试, key={}", cacheKey, e);
            throw e; // 抛异常触发 RocketMQ 重试（最多16次，之后进死信队列）
        }
    }
}
