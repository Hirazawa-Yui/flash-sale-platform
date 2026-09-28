package com.hmdp.metrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 秒杀两条路径的服务端延迟埋点。
 *
 * 【注册顺序是关键】必须在 MvcConfig 里注册在 LoginInterceptor **之前**：
 *   1. preHandle 先执行 → 测量窗口包含登录拦截器的 HGETALL + EXPIRE，
 *      这才是客户端真正体验到的那段时间；
 *   2. Spring 的 triggerAfterCompletion 从 interceptorIndex 往回走，
 *      注册在前意味着 LoginInterceptor 返回 401 时本拦截器的 afterCompletion
 *      仍会执行，401 能被统计到。注册在后则 401 完全不可见。
 */
@Component
public class MetricsInterceptor implements HandlerInterceptor {

    private static final String START_ATTR = "seckill.metrics.startNs";

    @Autowired
    private SeckillMetrics metrics;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 用 request attribute 而不是 ThreadLocal：afterCompletion 天然能拿到，
        // 不需要额外的 remove 清理，也不会在异步派发时串扰。
        request.setAttribute(START_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object started = request.getAttribute(START_ATTR);
        if (!(started instanceof Long)) {
            return;
        }

        String uri = request.getRequestURI();
        boolean async;
        if (uri.startsWith("/order/seckill")) {
            async = true;
            metrics.asyncRequests.increment();
        } else if (uri.startsWith("/test/seckill-sync")) {
            async = false;
            metrics.syncRequests.increment();
        } else {
            return;
        }

        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            // 非 2xx 只计数、不计入延迟样本。
            // 过期 token 产生的 401 只要几十微秒，混进分布会把 P95/P99 拉**低**，
            // 让路径显得比实际更快 —— 那是在骗自己。
            //
            // 注意：业务拒绝（Result.fail("库存不足")，HTTP 200）**要**算进样本 ——
            // 它完整走了 Redis + Lua 路径，是这个分布的真实组成部分。
            metrics.non2xx.increment();
            return;
        }
        metrics.recordLatency(async, System.nanoTime() - (Long) started);
    }
}
