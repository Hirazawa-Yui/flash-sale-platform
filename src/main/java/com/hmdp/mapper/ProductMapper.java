package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品 Mapper 接口
 */
public interface ProductMapper extends BaseMapper<Product> {

    List<Product> queryProductOfStore(@Param("shopId") Long shopId);

    /** 查询所有秒杀商品（type=1且status=1），含秒杀库存和时间信息 */
    List<Product> querySeckillProducts();
}
