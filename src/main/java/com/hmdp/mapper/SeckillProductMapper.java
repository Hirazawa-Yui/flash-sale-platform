package com.hmdp.mapper;

import com.hmdp.entity.SeckillProduct;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 秒杀商品表 Mapper 接口
 */
public interface SeckillProductMapper extends BaseMapper<SeckillProduct> {

    // 数据库行锁解决超卖问题
    @Select("select * from tb_seckill_product where product_id = #{id} for update")
    SeckillProduct getByIdForUpdate(@Param("id") Long id);
}
