package com.hmdp.service.impl;

import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // // 3. 保存验证码到session
        // session.setAttribute("code", code);

        // 3. 保存验证码到Redis并设置有效期
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4. 发送验证码
        log.debug("发送验证码成功，验证码：{}", code);

        return Result.ok();
    }

    /**
     * 登录功能
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 这里原本的Session方式代码存在问题，前后的手机号不是同一个也能登录，应该用包含手机号作为key

        // 1. 验证手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        /* // 2. 从session获取验证码并校验
        Object code = session.getAttribute("code");
        if (code == null || !code.toString().equals(loginForm.getCode())) {
            return Result.fail("验证码错误！");
        } */

        // 2. 从redis获取验证码并校验
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误！");
        }

        // 3. 根据手机号查询用户，mybatis-plus方式
        User user = query().eq("phone", phone).one();

        // 4. 用户不存在，则创建新用户并保存到数据库完成注册
        if (user == null) {
            user = createUserWithPhone(loginForm);
        }

        /* // 5. 保存用户到session，用于后续拦截器登录验证
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        session.setAttribute("user", userDTO); */

        // 5. 使用UUID随机生成token作为key，以Hash格式保存用户到redis，用于后续拦截器登录验证
        String token = UUID.randomUUID().toString(true);

        //  将user转为userDTO再转为Map
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);

        // 当前使用StringRedisTemplate，数据格式需要全部为String
        userMap.replaceAll((key, value) -> value == null ? "" : value.toString());

        // 以hash格式保存用户到redis
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);

        // 设置token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 6. 返回token给客户端（注意是不带前缀的LOGIN_USER_KEY）
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));

        int offset = now.getDayOfMonth() - 1;

        Boolean success = stringRedisTemplate.opsForValue().setBit(key, offset, true);
        if (Boolean.TRUE.equals(success)) {
            return Result.ok();
        }

        return Result.fail("签到失败");
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));

        int days = now.getDayOfMonth();

        // bitfield key get u Days 0
        List<Long> list = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(days))
                        .valueAt(0));
        if (list == null || list.isEmpty()) {
            return Result.ok(0);
        }

        long num = list.get(0);
        int count = 0;

        while ((num & 1) == 1) {
            count++;
            num = num >>> 1; // 无符号右移
        }

        return Result.ok(count);
    }

    /**
     * 根据手机号创建用户并保存到数据库
     *
     * @param loginForm
     * @return
     */
    private User createUserWithPhone(LoginFormDTO loginForm) {

        // 1. 创建用户
        User user = new User();
        user.setPhone(loginForm.getPhone());
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        // 2. 保存用户到数据库，mybatis-plus方式
        save(user);

        return user;
    }
}
