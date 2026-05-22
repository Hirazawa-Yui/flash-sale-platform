package com.hmdp.mapper;

import com.hmdp.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单 Mapper 接口
 */
public interface OrderMapper extends BaseMapper<Order> {

    /** 压测校验用：该商品已落库的订单总数。 */
    @Select("SELECT COUNT(*) FROM tb_order WHERE product_id = #{productId}")
    long countByProductId(@Param("productId") Long productId);

    /**
     * 压测校验用：该商品的下单用户去重数。
     * 与 countByProductId 相等即证明"一人一单"成立。
     *
     * 刻意拆成两条标量查询而不是一条 COUNT(*), COUNT(DISTINCT) 返回 Map：
     * MyBatis-Plus 开了 mapUnderscoreToCamelCase，map 结果的 key 大小写是个坑，
     * 而这是读取路径、不在热路径上，多一次查询无所谓。
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM tb_order WHERE product_id = #{productId}")
    long countDistinctUsersByProductId(@Param("productId") Long productId);

}
