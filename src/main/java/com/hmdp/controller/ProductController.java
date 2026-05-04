package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Product;
import com.hmdp.service.IProductService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 商品 前端控制器
 */
@RestController
@RequestMapping("/product")
public class ProductController {

    @Resource
    private IProductService productService;

    /**
     * 新增普通商品
     * @param product 商品信息
     * @return 商品id
     */
    @PostMapping
    public Result addProduct(@RequestBody Product product) {
        productService.save(product);
        return Result.ok(product.getId());
    }

    /**
     * 新增秒杀商品
     * @param product 商品信息，包含秒杀信息
     * @return 商品id
     */
    @PostMapping("seckill")
    public Result addSeckillProduct(@RequestBody Product product) {
        productService.addSeckillProduct(product);
        return Result.ok(product.getId());
    }

    /**
     * 查询店铺的商品列表
     * @param shopId 店铺id
     * @return 商品列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryProductOfStore(@PathVariable("shopId") Long shopId) {
       return productService.queryProductOfStore(shopId);
    }

    /**
     * 查询所有秒杀商品列表
     * @return 秒杀商品列表（含库存和时间）
     */
    @GetMapping("/seckill/list")
    public Result querySeckillProducts() {
        return productService.querySeckillProducts();
    }

    /**
     * 查询单个商品详情
     * @param id 商品id
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public Result queryProductById(@PathVariable("id") Long id) {
        return productService.queryProductById(id);
    }
}
