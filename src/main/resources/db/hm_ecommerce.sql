/*
 Navicat Premium Data Transfer

 Source Server         : local
 Source Server Type    : MySQL
 Source Server Version : 50622
 Source Host           : localhost:3306
 Source Schema         : hm_ecommerce

 Target Server Type    : MySQL
 Target Server Version : 50622
 File Encoding         : 65001

 Date: 28/07/2026
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tb_shop_type (店铺分类)
-- ----------------------------
DROP TABLE IF EXISTS `tb_shop_type`;
CREATE TABLE `tb_shop_type`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型名称',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `sort` int(3) UNSIGNED NULL DEFAULT NULL COMMENT '顺序',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_shop_type (电商分类)
-- ----------------------------
INSERT INTO `tb_shop_type` VALUES (1, '手机数码', '/types/phone.png', 1, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (2, '电脑办公', '/types/pc.png', 2, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (3, '服装鞋包', '/types/clothes.png', 3, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (4, '美妆护肤', '/types/beauty.png', 4, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (5, '食品生鲜', '/types/food.png', 5, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (6, '家居家装', '/types/home.png', 6, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (7, '运动户外', '/types/sport.png', 7, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (8, '图书文娱', '/types/book.png', 8, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (9, '汽车用品', '/types/car.png', 9, '2024-01-01 10:00:00', '2024-01-01 10:00:00');
INSERT INTO `tb_shop_type` VALUES (10, '母婴玩具', '/types/baby.png', 10, '2024-01-01 10:00:00', '2024-01-01 10:00:00');

-- ----------------------------
-- Table structure for tb_shop (店铺)
-- ----------------------------
DROP TABLE IF EXISTS `tb_shop`;
CREATE TABLE `tb_shop`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '店铺名称',
  `type_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺类型的id',
  `images` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '店铺图片，多个图片以'',''隔开',
  `area` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商圈，例如陆家嘴',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '地址',
  `x` double UNSIGNED NOT NULL COMMENT '经度',
  `y` double UNSIGNED NOT NULL COMMENT '维度',
  `avg_price` bigint(10) UNSIGNED NULL DEFAULT NULL COMMENT '均价，取整数',
  `sold` int(10) UNSIGNED ZEROFILL NOT NULL COMMENT '销量',
  `comments` int(10) UNSIGNED ZEROFILL NOT NULL COMMENT '评论数量',
  `score` int(2) UNSIGNED ZEROFILL NOT NULL COMMENT '评分，1~5分，乘10保存，避免小数',
  `open_hours` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '营业时间，例如 10:00-22:00',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `foreign_key_type`(`type_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_shop (电商品牌旗舰店)
-- ----------------------------
INSERT INTO `tb_shop` VALUES (1, '华为官方旗舰店', 1, '/imgs/shops/huawei.jpg', '科技园区', '杭州市滨江区江南大道3888号', 120.208519, 30.190439, 3999, 0000158230, 0000089560, 49, '09:00-22:00', '2024-01-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (2, 'Apple官方旗舰店', 1, '/imgs/shops/apple.jpg', '科技园区', '杭州市西湖区学院路77号', 120.134553, 30.281194, 5999, 0000256100, 0000152300, 48, '09:00-22:00', '2024-01-01 10:01:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (3, '小米官方旗舰店', 1, '/imgs/shops/xiaomi.jpg', '科技园区', '杭州市滨江区网商路599号', 120.197326, 30.187409, 1999, 0000189600, 0000112400, 47, '09:00-22:00', '2024-01-01 10:02:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (4, '联想官方旗舰店', 2, '/imgs/shops/lenovo.jpg', '科技园区', '杭州市拱墅区祥园路88号', 120.132647, 30.336819, 4999, 0000098700, 0000054300, 46, '09:00-21:00', '2024-01-01 10:03:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (5, '戴尔官方旗舰店', 2, '/imgs/shops/dell.jpg', '科技园区', '杭州市西湖区文三路478号', 120.135874, 30.281054, 5499, 0000089200, 0000045600, 47, '09:00-21:00', '2024-01-01 10:04:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (6, '优衣库官方旗舰店', 3, '/imgs/shops/uniqlo.jpg', '湖滨商圈', '杭州市上城区延安路258号', 120.164497, 30.255411, 199, 0000563200, 0000352100, 48, '10:00-22:00', '2024-01-01 10:05:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (7, '兰蔻官方旗舰店', 4, '/imgs/shops/lancome.jpg', '湖滨商圈', '杭州市上城区解放路199号', 120.169230, 30.248720, 599, 0000321500, 0000213600, 49, '10:00-22:00', '2024-01-01 10:06:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (8, '雅诗兰黛官方旗舰店', 4, '/imgs/shops/esteelauder.jpg', '武林商圈', '杭州市下城区武林路88号', 120.162653, 30.270472, 699, 0000287400, 0000189200, 48, '10:00-22:00', '2024-01-01 10:07:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (9, '三只松鼠旗舰店', 5, '/imgs/shops/3squirrels.jpg', '九堡商圈', '杭州市江干区九堡镇九盛路9号', 120.278145, 30.311209, 49, 0000985600, 0000652100, 49, '09:00-23:00', '2024-01-01 10:08:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (10, '宜家官方旗舰店', 6, '/imgs/shops/ikea.jpg', '城北商圈', '杭州市余杭区乔司街道乔莫西路5号', 120.280851, 30.352623, 299, 0000365200, 0000221500, 46, '10:00-21:00', '2024-01-01 10:09:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (11, '耐克官方旗舰店', 7, '/imgs/shops/nike.jpg', '湖滨商圈', '杭州市上城区延安路309号', 120.164116, 30.256688, 599, 0000458900, 0000298700, 49, '10:00-22:00', '2024-01-01 10:10:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (12, '阿迪达斯官方旗舰店', 7, '/imgs/shops/adidas.jpg', '武林商圈', '杭州市拱墅区武林广场1号', 120.164576, 30.273316, 499, 0000398700, 0000256400, 47, '10:00-22:00', '2024-01-01 10:11:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (13, '乐高官方旗舰店', 10, '/imgs/shops/lego.jpg', '城西商圈', '杭州市西湖区古墩路588号', 120.100508, 30.290832, 399, 0000215600, 0000132500, 50, '10:00-21:30', '2024-01-01 10:12:00', '2024-06-01 10:00:00');
INSERT INTO `tb_shop` VALUES (14, 'Babycare官方旗舰店', 10, '/imgs/shops/babycare.jpg', '滨江商圈', '杭州市滨江区建业路511号', 120.185693, 30.177135, 199, 0000256800, 0000163200, 48, '09:00-22:00', '2024-01-01 10:13:00', '2024-06-01 10:00:00');

-- ----------------------------
-- Table structure for tb_product (商品表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_product`;
CREATE TABLE `tb_product`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '店铺id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品名称',
  `sub_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '副标题',
  `description` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品描述',
  `image` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品图片URL',
  `price` bigint(10) UNSIGNED NOT NULL COMMENT '秒杀价格，单位是分。例如99900代表999元',
  `original_price` bigint(10) NOT NULL COMMENT '商品原价，单位是分。例如129900代表1299元',
  `type` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0,普通商品；1,秒杀商品',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '1,上架; 2,下架; 3,过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_product (商品数据)
-- ----------------------------
-- === 秒杀商品 (type=1) ===
INSERT INTO `tb_product` VALUES (1, 1, '华为 Mate 60 Pro', '麒麟9000S 卫星通话 12GB+512GB', '【新品首发】6.82英寸OLED曲面屏|卫星通话功能|XMAGE影像|88W超级快充|IP68防水|HarmonyOS 4.0', '/imgs/products/huawei_mate60pro.jpg', 699900, 799900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (2, 2, 'Apple iPhone 15 Pro Max', 'A17 Pro芯片 256GB 原色钛金属', '【限时秒杀】6.7英寸超视网膜XDR显示屏|钛金属设计|USB-C接口|Action按钮|4800万像素主摄|iOS 17', '/imgs/products/iphone15promax.jpg', 899900, 999900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (3, 3, '小米14 Ultra', '骁龙8Gen3 徕卡光学 16GB+512GB', '【爆款秒杀】徕卡专业街拍模式|骁龙8Gen3处理器|90W澎湃快充|LTPO 2K超视感屏|5000万徕卡全焦段四摄', '/imgs/products/xiaomi14ultra.jpg', 549900, 599900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (4, 4, '联想拯救者 Y9000P', 'i9-13900HX RTX4060 16GB+1TB', '【游戏本秒杀】16英寸2.5K 240Hz高刷屏|140W满血显卡|DDR5内存|霜刃Pro散热系统5.0|雷电4接口', '/imgs/products/lenovo_y9000p.jpg', 849900, 999900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (5, 11, 'Nike Air Jordan 1 芝加哥', '经典复刻 红白黑配色 男女同款', '【限量秒杀】AJ1 OG芝加哥配色|优质皮革鞋面|Air气垫缓震|耐磨橡胶外底|经典飞翼Logo|收藏必备', '/imgs/products/aj1_chicago.jpg', 99900, 149900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (6, 7, '兰蔻小黑瓶精华肌底液 50ml', '第二代 修护维稳 强韧肌肤屏障', '【美妆秒杀】全新肌底液|10%二裂酵母精粹|修护肌肤屏障|淡化细纹|提亮肤色|适合所有肤质|法国进口', '/imgs/products/lancome_genifique.jpg', 59900, 89900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (7, 13, '乐高 机械组 兰博基尼 Sián FKP 37', '1:8超跑 3696颗粒 成人收藏级', '【限量秒杀】科技系列旗舰|V12引擎+可移动活塞|8速变速箱|可调节尾翼|剪刀门设计|收藏级展示品', '/imgs/products/lego_sian.jpg', 249900, 349900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (8, 9, '三只松鼠 每日坚果礼盒 750g', '30包混合坚果 年货送礼佳品', '【超值秒杀】甄选6种坚果果干|每日1包健康好搭档|无添加防腐剂|独立小包装|年货送礼首选', '/imgs/products/3squirrels_nuts.jpg', 7990, 14900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (9, 10, '宜家 SKÅDIS 斯考迪斯 洞洞板套装', '白色 76x56cm 含配件包', '【家居秒杀】模块化收纳系统|充分利用墙面空间|含多种挂钩与收纳盒|轻松组合|北欧简约设计|办公室/厨房/卧室通用', '/imgs/products/ikea_skadis.jpg', 14900, 24900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (10, 2, 'Apple AirPods Pro 第二代', '主动降噪 自适应透明模式 USB-C接口', '【数码秒杀】H2芯片|自适应音频|个性化空间音频|触控操作|MagSafe充电盒|IPX4抗汗抗水|6小时续航', '/imgs/products/airpods_pro2.jpg', 169900, 189900, 1, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');

-- === 普通商品 (type=0) ===
INSERT INTO `tb_product` VALUES (11, 1, '华为 Pura 70 Ultra', '超聚光伸缩摄像头 16GB+1TB', '【旗舰影像】XMAGE华为影像|超聚光伸缩主摄|高清长焦微距|昆仑玻璃|HarmonyOS系统|北斗卫星消息', '/imgs/products/huawei_pura70.jpg', 899900, 1099900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (12, 2, 'Apple iPad Pro 2024', 'M4芯片 13英寸 256GB 深空黑色', '【性能怪兽】Ultra Retina XDR显示屏|M4芯片颠覆性能|轻薄5.1mm厚度|支持Apple Pencil Pro|iPadOS', '/imgs/products/ipad_pro2024.jpg', 929900, 1029900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (13, 3, '小米 Redmi K70 Pro', '骁龙8Gen3 2K直屏 12GB+256GB', '【性价比之王】第二代骁龙8|2K中国屏|5000mAh+120W秒充|光影猎人800传感器|金属中框|冰封散热', '/imgs/products/redmi_k70pro.jpg', 269900, 329900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (14, 5, '戴尔 XPS 14 2024', 'Intel Core Ultra 7 16GB+512GB', '【轻薄本旗舰】14英寸3.5K OLED触控屏|InfinityEdge微边框|杜比全景声|Evo平台认证|CNC铝合金机身', '/imgs/products/dell_xps14.jpg', 999900, 1199900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (15, 6, '优衣库 高性能空气棉圆领T恤', '男女同款 多色可选 吸汗速干', '【夏季热销】AIRism科技面料|吸汗速干|接触冷感|抗菌防臭|可机洗|多色多尺码可选|日常百搭基础款', '/imgs/products/uniqlo_airism.jpg', 9900, 14900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (16, 8, '雅诗兰黛 DW持妆粉底液 30ml', '油皮亲妈 持久控油 24色可选', '【底妆明星】24小时持妆不脱妆|控油遮瑕|哑光妆效|SPF10防晒|无油配方|不堵塞毛孔|亚洲限定色号', '/imgs/products/esteelauder_dw.jpg', 29900, 41000, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (17, 12, 'Adidas Ultraboost Light 跑鞋', '超轻缓震 透气针织 男女跑步鞋', '【跑步首选】Light BOOST中底科技|Primeknit+弹性针织鞋面|Continental马牌橡胶外底|L.E.P.抗扭转系统', '/imgs/products/adidas_ub.jpg', 79900, 109900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (18, 10, '宜家 MALM 马尔姆 储物床架', '白色 150x200cm 带4个抽屉', '【卧室爆款】气压式抽屉储物|简约北欧设计|实木贴皮+中纤板|超大储物空间|可搭配任意床垫|10年质保', '/imgs/products/ikea_malm.jpg', 199900, 299900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (19, 14, 'Babycare 纸尿裤 XL码 108片', '皇室弱酸 超薄透气 12-17kg适用', '【宝妈必囤】弱酸性表层防红屁屁|3D珍珠面层干爽不反渗|莱卡橡筋不勒大腿|日本住友高分子吸水|透气底膜不闷热', '/imgs/products/babycare_diaper.jpg', 18900, 26900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (20, 6, '优衣库 高级轻型羽绒夹克', '男女同款 750蓬松度 超轻保暖', '【秋冬必备】750+蓬松度白鸭绒|超轻约200g|可收纳至收纳袋|防水防静电面料|多色可选|保暖时尚兼备', '/imgs/products/uniqlo_ultralightdown.jpg', 39900, 59900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (21, 1, '华为 Watch GT 4 Pro', '钛金属表壳 46mm 14天长续航', '【智能腕表】AMOLED显示屏|ECG心电分析|双频五星GPS|100+运动模式|蓝牙通话|HarmonyOS|深度防水', '/imgs/products/huawei_watchgt4.jpg', 239900, 289900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (22, 3, '小米电视 S75 Mini LED', '75英寸 512分区 144Hz高刷 4GB+64GB', '【客厅影院】Mini LED 512背光分区|144Hz疾速刷新|DCI-P3 93%广色域|杜比视界+全景声|WiFi 6|金属全面屏', '/imgs/products/xiaomi_tv75.jpg', 449900, 549900, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');
INSERT INTO `tb_product` VALUES (23, 7, '兰蔻 极光精华水 250ml', '净澈焕肤 匀净透亮 油皮挚爱', '【精华水热销】法国千叶山毛榉嫩芽精粹|温和代谢老废角质|收敛毛孔控油|提亮暗沉肤色|水油平衡|不含酒精', '/imgs/products/lancome_clarifique.jpg', 45900, 69000, 0, 1, '2024-06-01 10:00:00', '2024-06-01 10:00:00');

-- ----------------------------
-- Table structure for tb_seckill_product (秒杀商品表，与商品是一对一关系)
-- ----------------------------
DROP TABLE IF EXISTS `tb_seckill_product`;
CREATE TABLE `tb_seckill_product`  (
  `product_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的商品id',
  `stock` int(8) NOT NULL COMMENT '库存',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `begin_time` timestamp NULL DEFAULT NULL COMMENT '生效时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '失效时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '秒杀商品表，与商品是一对一关系' ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_seckill_product (10个秒杀活动的库存和时间)
-- 秒杀时间设置为当前前后，确保测试时可以正常访问
-- ----------------------------
INSERT INTO `tb_seckill_product` VALUES (1, 200, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (2, 150, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (3, 300, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (4, 100, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (5, 80, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (6, 500, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (7, 50, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (8, 1000, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (9, 200, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');
INSERT INTO `tb_seckill_product` VALUES (10, 300, '2024-06-01 10:00:00', '2024-01-01 00:00:00', '2028-12-31 23:59:59', '2024-06-01 10:00:00');

-- ----------------------------
-- Table structure for tb_order (订单表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_order`;
CREATE TABLE `tb_order`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '下单的用户id',
  `product_id` bigint(20) UNSIGNED NOT NULL COMMENT '购买的商品id',
  `pay_type` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_user
-- ----------------------------
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号码',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '昵称，默认是用户id',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '人物头像',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniqe_key_phone`(`phone`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1010 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_user (保留原有测试用户)
-- ----------------------------
INSERT INTO `tb_user` VALUES (1, '13686869696', '', '小鱼同学', '/imgs/blogs/blog1.jpg', '2021-12-24 10:27:19', '2022-01-11 16:04:00');
INSERT INTO `tb_user` VALUES (2, '13838411438', '', '可可今天不吃肉', '/imgs/icons/kkjtbcr.jpg', '2021-12-24 15:14:39', '2021-12-28 19:58:04');
INSERT INTO `tb_user` VALUES (5, '13456789001', '', '可爱多', '/imgs/icons/user5-icon.png', '2022-01-07 16:11:33', '2022-03-11 09:09:20');

-- ----------------------------
-- Table structure for tb_user_info
-- ----------------------------
DROP TABLE IF EXISTS `tb_user_info`;
CREATE TABLE `tb_user_info`  (
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，用户id',
  `city` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '城市名称',
  `introduce` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '个人介绍',
  `fans` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '粉丝数量',
  `followee` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '关注的人的数量',
  `gender` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '性别，0：男，1：女',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `credits` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '积分',
  `level` tinyint(1) UNSIGNED NULL DEFAULT 0 COMMENT '会员等级',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_blog (店铺评价)
-- ----------------------------
DROP TABLE IF EXISTS `tb_blog`;
CREATE TABLE `tb_blog`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `shop_id` bigint(20) NOT NULL COMMENT '店铺id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `images` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '评价的照片，最多9张，多张以'',''隔开',
  `content` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评价的文字描述',
  `liked` int(8) UNSIGNED NULL DEFAULT 0 COMMENT '点赞数量',
  `comments` int(8) UNSIGNED NULL DEFAULT NULL COMMENT '评论数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Records of tb_blog (电商店铺评价)
-- ----------------------------
INSERT INTO `tb_blog` VALUES (4, 1, 2, '华为 Mate 60 Pro 到手实测，真的是国货之光！', '/imgs/blogs/blog1.jpg', '终于抢到了华为Mate60Pro！必须来评价一下这家店。<br/><br/>📱物流：下单第二天就到了，包装严实，顺丰包邮。<br/><br/>📱商品：手机做工精致，昆仑玻璃手感极佳，卫星通话功能在户外探险时真的是救命稻草。<br/><br/>📱性能：麒麟9000S虽然跑分不是最高，但日常使用丝般顺滑，发热控制也很好。<br/><br/>总之这波不亏，国产旗舰值得拥有！', 126, 35, '2024-06-15 10:30:00', '2024-06-20 15:00:00');
INSERT INTO `tb_blog` VALUES (5, 2, 1, 'Apple Store 买的 iPhone 15 Pro Max，钛合金质感绝了', '/imgs/blogs/blog2.jpg', '在Apple旗舰店入手了15PM，简单分享一下使用感受。<br/><br/>🔧钛金属边框：轻了不少，手感比不锈钢好太多，重点是终于不粘指纹了！<br/><br/>📷影像：5倍光学变焦真的很实用，拍远处演出的神器。视频录制依然是天花板。<br/><br/>🔋续航：比14PM提升明显，一天一充无压力。USB-C接口终于可以和安卓共用了。<br/><br/>店铺服务也很好，支持以旧换新，省了不少钱。', 203, 58, '2024-06-16 14:20:00', '2024-06-21 10:00:00');
INSERT INTO `tb_blog` VALUES (6, 11, 2, 'Nike旗舰店的服务太赞了，AJ1芝加哥配色开箱', '/imgs/blogs/blog3.jpg', 'AJ1芝加哥复刻终于到了！<br/><br/>👟包装：Nike官方包装精美，每只鞋都独立包装，还送了一副备用鞋带。<br/><br/>👟做工：这次的皮质用料手感很好，红色饱和度调得很正，终于不是之前的橙色了。<br/><br/>👟尺码：建议买大0.5码，AJ1前掌偏窄。<br/><br/>店铺发货速度快，客服很耐心，下次买鞋还会选这家。', 89, 22, '2024-07-01 09:00:00', '2024-07-03 15:30:00');

-- ----------------------------
-- Table structure for tb_blog_comments
-- ----------------------------
DROP TABLE IF EXISTS `tb_blog_comments`;
CREATE TABLE `tb_blog_comments`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '评价id',
  `parent_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的1级评论id，如果是一级评论，则值为0',
  `answer_id` bigint(20) UNSIGNED NOT NULL COMMENT '回复的评论id',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '回复的内容',
  `liked` int(8) UNSIGNED NULL DEFAULT NULL COMMENT '点赞数',
  `status` tinyint(1) UNSIGNED NULL DEFAULT NULL COMMENT '状态，0：正常，1：被举报，2：禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_follow
-- ----------------------------
DROP TABLE IF EXISTS `tb_follow`;
CREATE TABLE `tb_follow`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `follow_user_id` bigint(20) UNSIGNED NOT NULL COMMENT '关联的用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

-- ----------------------------
-- Table structure for tb_sign
-- ----------------------------
DROP TABLE IF EXISTS `tb_sign`;
CREATE TABLE `tb_sign`  (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `year` year NOT NULL COMMENT '签到的年',
  `month` tinyint(2) NOT NULL COMMENT '签到的月',
  `date` date NOT NULL COMMENT '签到的日期',
  `is_backup` tinyint(1) UNSIGNED NULL DEFAULT NULL COMMENT '是否补签',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Compact;

SET FOREIGN_KEY_CHECKS = 1;
