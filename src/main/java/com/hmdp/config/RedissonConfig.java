package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String REDIS_HOST;
    @Value("${spring.redis.port}")
    private int REDIS_PORT;
    @Value("${spring.redis.password}")
    private String REDIS_PASSWORD;

    @Bean
    public RedissonClient redissonClient() {
        log.info("开始初始化Redisson: {},{},{}", REDIS_HOST, REDIS_PORT, REDIS_PASSWORD);
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + REDIS_HOST + ":" + REDIS_PORT)
                .setPassword(REDIS_PASSWORD);
        return Redisson.create(config);
    }
}
