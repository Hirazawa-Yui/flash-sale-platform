package com.hmdp.controller;


import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitMode;
import com.hmdp.dto.Result;
import com.hmdp.service.IOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 订单 前端控制器
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private IOrderService orderService;

    /**
     * 秒杀商品
     * @param productId 商品id
     * @return 订单id
     */
    // @RateLimit(mode = RateLimitMode.USER, limit = 1, windowSeconds = 5, key = "seckill")
    @PostMapping("seckill/{id}")
    public Result seckillProduct(@PathVariable("id") Long productId) {
        return orderService.seckillProduct(productId);
    }

    /**
     * 查询当前用户的订单列表（分页）
     * @param current 页码，默认1
     * @param size    每页条数，默认10
     * @return 订单列表
     */
    @GetMapping("/list")
    public Result queryMyOrders(@RequestParam(defaultValue = "1") Integer current,
                                @RequestParam(defaultValue = "10") Integer size) {
        return orderService.queryMyOrders(current, size);
    }

    /**
     * 查询订单详情
     * @param orderId 订单id
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable("id") Long orderId) {
        return orderService.queryOrderById(orderId);
    }
}
