package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 店铺id
     */
    private Long shopId;

    /**
     * 商品名称
     */
    private String title;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 商品图片URL
     */
    private String image;

    /**
     * 秒杀价格（单位：分）
     */
    private Long price;

    /**
     * 商品原价（单位：分）
     */
    private Long originalPrice;

    /**
     * 商品类型 0：普通商品；1：秒杀商品
     */
    private Integer type;

    /**
     * 商品状态 1：上架；2：下架；3：过期
     */
    private Integer status;

    /**
     * 库存
     */
    @TableField(exist = false)
    private Integer stock;

    /**
     * 生效时间
     */
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /**
     * 失效时间
     */
    @TableField(exist = false)
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
