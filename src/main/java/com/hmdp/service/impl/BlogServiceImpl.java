package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Integer id) {
        Blog blog = getById(id);

        if (blog == null) {
            return Result.fail("博客不存在");
        }

        queryBlogUserAndLikes(blog);

        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();

        // 查询用户
        records.forEach(this::queryBlogUserAndLikes);

        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 从redis判断用户是否已经点赞过
        String key = BLOG_LIKED_KEY + id;
        String value = String.valueOf(userId);
        Boolean isLiked = (stringRedisTemplate.opsForZSet().score(key, value) != null);

        if (Boolean.TRUE.equals(isLiked)) {
            // 3. 如果已点赞，取消点赞，数据库点赞数减一
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 3.1 将用户id移出Redis的zset
            if (isSuccess)
                stringRedisTemplate.opsForZSet().remove(key, value);
        } else {// 4. 如果未点赞过，数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 4.1 将用户id存入Redis的zset，zset的key包含blog的id
            if (isSuccess)
                stringRedisTemplate.opsForZSet().add(key, value, System.currentTimeMillis());
        }

        return Result.ok();
    }

    /**
     * 查询博客最早的5名点赞用户，返回List<UserDTO>
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 获取点赞用户的id集合
        String key = BLOG_LIKED_KEY + id;
        Set<String> userIds = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (userIds == null || userIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        String idsStr = StrUtil.join(",", userIds);
        // select * from tb_user WHERE id IN () ORDER BY FIELD(id, x1, x2...)
        List<UserDTO> list = userService.query()
                .in("id", userIds).last("ORDER BY FIELD(id," + idsStr + ")").list()
                .stream()
                .map((user) -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(list);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean success = save(blog);
        if (!success) {
            return Result.fail("博客发布失败");
        }

        // 获取所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> followers = followService.query().eq("follow_user_id", user.getId()).list();

        Long blogId = blog.getId();
        for (Follow follower : followers) {
            // 将博客id推送到粉丝收件箱
            Long followerUserId = follower.getUserId();
            String key = FEED_KEY + followerUserId;
            stringRedisTemplate.opsForZSet().add(key, blogId.toString(), System.currentTimeMillis());
        }

        // 返回博客id
        return Result.ok(blogId);
    }

    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        // 获取当前用户，找到ta的收件箱
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;

        // 游标分页方式: ZREVRANGEBYSCORE key max min offset count
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastId, offset, 3);

        if (tuples == null || tuples.isEmpty()) {
            return Result.ok();
        }

        // 获取收件箱中的blogId集合
        // 获取最后一次读到的时间戳 minTime/lastId，以及分页结果中和它时间戳相同的一共有几条（offset）
        List<Long> blogIds = new ArrayList<>(tuples.size());
        long _lastId = 0;
        int _offset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            // 获取blogId
            blogIds.add(Long.valueOf(tuple.getValue()));
            // 获取时间戳
            long score = tuple.getScore().longValue();

            // 计算offset
            if (score == _lastId) {
                _offset++;
            } else {
                _lastId = score;
                _offset = 1;
            }
        }

        // 通过blogId查询博客
        // List<Blog> blogs = listByIds(blogIds); // 不保证顺序
        String blogIdsStr = StrUtil.join(",", blogIds);
        List<Blog> blogs = query()
                .in("id", blogIds)
                .last("ORDER BY FIELD (id, " + blogIdsStr + ")")
                .list();

        // 查询blog的作者以及点赞信息
        blogs.forEach(this::queryBlogUserAndLikes);

        // 返回封装的结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(_lastId);
        scrollResult.setOffset(_offset);

        return Result.ok(scrollResult);
    }

    private void queryBlogUserAndLikes(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        // 判断当前登录用户是否给blog点过赞，给isLike赋值
        String key = BLOG_LIKED_KEY + blog.getId();
        String loginId = String.valueOf(UserHolder.getUser().getId());
        blog.setIsLike((stringRedisTemplate.opsForZSet().score(key, loginId)) != null);
    }
}
