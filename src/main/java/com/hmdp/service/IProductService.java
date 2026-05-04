package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Product;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 商品 服务类
 */
public interface IProductService extends IService<Product> {

    Result queryProductOfStore(Long shopId);

    void addSeckillProduct(Product product);

    /** 查询所有秒杀商品列表（含库存和秒杀时间） */
    Result querySeckillProducts();

    /** 查询单个商品详情（含秒杀库存和时间信息） */
    Result queryProductById(Long id);
}
