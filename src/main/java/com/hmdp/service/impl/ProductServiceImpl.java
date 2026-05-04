package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Product;
import com.hmdp.mapper.ProductMapper;
import com.hmdp.entity.SeckillProduct;
import com.hmdp.service.ISeckillProductService;
import com.hmdp.service.IProductService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 商品 服务实现类
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    @Resource
    private ISeckillProductService seckillProductService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryProductOfStore(Long shopId) {
        // 查询商品信息
        List<Product> products = getBaseMapper().queryProductOfStore(shopId);
        // 返回结果
        return Result.ok(products);
    }

    @Override
    public Result querySeckillProducts() {
        List<Product> products = getBaseMapper().querySeckillProducts();
        return Result.ok(products);
    }

    @Override
    public Result queryProductById(Long id) {
        // 1. 查询商品基本信息
        Product product = getById(id);
        if (product == null) {
            return Result.fail("商品不存在");
        }

        // 2. 如果是秒杀商品，补充秒杀信息（库存、开始/结束时间）
        if (product.getType() != null && product.getType() == 1) {
            SeckillProduct sp = seckillProductService.getById(id);
            if (sp != null) {
                product.setStock(sp.getStock());
                product.setBeginTime(sp.getBeginTime());
                product.setEndTime(sp.getEndTime());
            }
        }

        return Result.ok(product);
    }

    @Override
    @Transactional
    public void addSeckillProduct(Product product) {
        // 保存商品
        save(product);
        // 保存秒杀信息
        SeckillProduct seckillProduct = new SeckillProduct();
        seckillProduct.setProductId(product.getId());
        seckillProduct.setStock(product.getStock());
        seckillProduct.setBeginTime(product.getBeginTime());
        seckillProduct.setEndTime(product.getEndTime());
        seckillProductService.save(seckillProduct);

        // 将新增的秒杀商品库存信息添加到Redis中，以做异步秒杀业务
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + product.getId(), product.getStock().toString());
    }
}
