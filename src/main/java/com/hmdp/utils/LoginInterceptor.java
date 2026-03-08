package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.metrics.SeckillMetrics;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    /** 压测埋点，可为 null（比如单测里直接 new）。 */
    private final SeckillMetrics metrics;

    // 通过构造函数的方式引入StringRedisTemplate，当LoginInterceptor被Spring创建时自动获得StringRedisTemplate
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, null);
    }

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate, SeckillMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.metrics = metrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        /* // 1. 获取当前Session中的user
        UserDTO userMap = (UserDTO) (request.getSession().getAttribute("userMap")); */

        // 1. 获取当前请求中的Token，并从中通过Redis获取用户
        String token = request.getHeader("authorization");
        if(token == null || token.isEmpty()){
            response.setStatus(401);
            return false;
        }

        // 压测埋点：只包住 Redis 往返，不含后面的 Bean 拷贝
        long redisT0 = System.nanoTime();

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

        // 2. 判断用户是否存在，entries方法不会返回null，至少是空map
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }

        // 3. 将Redis的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 3. 保存用户到ThreadLocal以便数据共享
        UserHolder.saveUser(userDTO);

        // 4.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,
                RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        if (metrics != null) {
            metrics.recordLoginRedis(System.nanoTime() - redisT0);
        }

        // 5. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
