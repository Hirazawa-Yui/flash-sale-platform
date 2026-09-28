package com.hmdp.config;

import com.hmdp.metrics.MetricsInterceptor;
import com.hmdp.utils.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private MetricsInterceptor metricsInterceptor;
    @Autowired
    private com.hmdp.metrics.SeckillMetrics seckillMetrics;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 【顺序敏感】MetricsInterceptor 必须先于 LoginInterceptor 注册。
        // 这样测量窗口才覆盖鉴权开销，且 LoginInterceptor 返回 401 时它仍能统计到。
        // 详见 MetricsInterceptor 类注释。
        registry.addInterceptor(metricsInterceptor)
                .addPathPatterns("/order/seckill/**", "/test/seckill-sync/**");

        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate, seckillMetrics)).excludePathPatterns(
                "/user/code",
                "/user/login",
                // "/blog/hot",
                "/shop/**",
                "/upload/**",
                "/shop-type/**",
                "/product/**",
                "/test/**"  // 压测辅助接口
        );
    }

}
