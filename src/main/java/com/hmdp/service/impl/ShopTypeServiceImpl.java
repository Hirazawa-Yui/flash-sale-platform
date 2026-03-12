package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商铺类型并做缓存
     * @return
     */
    @Override
    public List<ShopType> queryList() {

        // 1. 从redis查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY + "all";
        String shopListJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 存在则返回
        if (shopListJson != null) {
            List<ShopType> typeList = JSONUtil.toList(shopListJson, ShopType.class);
            return typeList;
        }

        // 3. 不存在则查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();

        if (typeList == null) {
            return null;
        }

        // 4. 存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        return typeList;
    }
}
