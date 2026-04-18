package com.hmdp.aop;

import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitMode;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;

/**
 * 滑动窗口限流 AOP 切面
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("rate_limit.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = buildKey(rateLimit);
        long now = System.currentTimeMillis();

        Long result = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(rateLimit.limit()),
                String.valueOf(rateLimit.windowSeconds() * 1000L),
                String.valueOf(now),
                UUID.randomUUID().toString()
        );

        if (result != null && result == 0) {
            log.warn("限流拦截: key={}, limit={}, window={}s",
                    key, rateLimit.limit(), rateLimit.windowSeconds());
            return Result.fail("请求过于频繁，请稍后再试");
        }

        return pjp.proceed();
    }

    /**
     * 根据限流模式构建 Redis Key
     */
    private String buildKey(RateLimit rateLimit) {
        String prefix = "rate_limit";
        String suffix = rateLimit.key().isEmpty() ? "" : ":" + rateLimit.key();
        RateLimitMode mode = rateLimit.mode();

        switch (mode) {
            case IP:
                String ip = getClientIp();
                return prefix + ":ip:" + ip + suffix;
            case USER:
                UserDTO user = UserHolder.getUser();
                String userId = (user != null) ? String.valueOf(user.getId()) : "anonymous";
                return prefix + ":user:" + userId + suffix;
            case GLOBAL:
            default:
                return prefix + ":global" + suffix;
        }
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
