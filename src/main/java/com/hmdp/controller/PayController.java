package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器 — 模拟支付回调，使用乐观锁解决支付回调与超时关单的并发冲突
 */
@RestController
@RequestMapping("/pay")
public class PayController {

    @Autowired
    private IOrderService orderService;

    /**
     * 模拟支付回调（第三方支付平台异步通知）
     * 乐观锁: UPDATE tb_order SET status=2 WHERE id=? AND status=1
     * 只有 status=1（未支付）的订单才能支付成功，防止与超时关单并发冲突
     *
     * @param orderId 订单id
     * @return 支付结果
     */
    @PutMapping("/callback/{orderId}")
    public Result payCallback(@PathVariable Long orderId) {
        boolean paid = orderService.payOrder(orderId);
        if (paid) {
            return Result.ok("支付成功");
        } else {
            // 影响行数为0 → status≠1，可能已被超时关单改为4
            return Result.fail("订单状态异常，可能已超时取消");
        }
    }
}
