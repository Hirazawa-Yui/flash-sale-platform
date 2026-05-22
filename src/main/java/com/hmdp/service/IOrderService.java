package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Order;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 订单 服务类
 */
public interface IOrderService extends IService<Order> {

    /**
     * 秒杀商品
     *
     * @param productId
     * @return
     */
    Result seckillProduct(Long productId);

    /**
     * 异步创建订单
     *
     * @param order
     * @return
     */
    void createOrder(Order order);

    /**
     * 支付订单（乐观锁），与超时关单互斥
     *
     * @param orderId 订单id
     * @return true-支付成功，false-订单状态已变更（可能已超时取消）
     */
    boolean payOrder(Long orderId);

    /**
     * 超时关单处理（乐观锁 + 回滚库存）
     *
     * @param orderId 订单id
     */
    void handleTimeoutClose(Long orderId);

    /**
     * 【压测对比用】同步秒杀（Redisson分布式锁 + DB查库存 + 乐观锁扣减）
     * 与 seckillProduct (Redis+Lua+RocketMQ) 形成性能对比
     *
     * @param productId
     * @return
     */
    Result seckillProductSync(Long productId);

    /**
     * 查询当前用户的订单列表（分页）
     *
     * @param current 页码
     * @param size    每页条数
     * @return 订单列表
     */
    Result queryMyOrders(Integer current, Integer size);

    /**
     * 查询订单详情
     *
     * @param orderId 订单id
     * @return 订单详情
     */
    Result queryOrderById(Long orderId);
}
