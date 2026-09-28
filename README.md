# 秒杀平台 Flash Sale Platform

基于 **Redis + Lua 原子扣减** 与 **RocketMQ 异步落库** 的秒杀系统后端，模拟大促瞬时抢购场景。

核心解决的是一件事：**把下单链路上的数据库写入剥离出去**，让突发流量下的接口耗时不再受 DB 行锁排队支配。

> 在 [黑马点评](https://www.itheima.com/) 工程的基础上扩展而来，**秒杀模块为独立设计实现**。工程底座提供登录、商铺、探店笔记等基础能力，秒杀链路由本项目的 `service/impl/OrderServiceImpl`、`resources/seckill.lua`、`mq/SeckillOrderConsumer` 组成。

---

## 一、性能数据

单机压测，2000 用户并发抢购 2000 件**同一商品**（全部成功，完整写入链路）：

| 指标 | 本方案 | 同步写库对照 | 差距 |
|---|---|---|---|
| 平均响应时间 | **9 ms** | 416 ms | 46× |
| 中位数 | 7 ms | 456 ms | 65× |
| P95 | 24 ms | 636 ms | 26× |
| P99 | 43 ms | 738 ms | 17× |
| 吞吐量 | 998 /s | 501 /s | 2× |
| 错误率 | 0% | 0% | — |

压测结束后 **2000 笔订单全部落库，无超卖、无重复下单**（9 项一致性恒等式全 PASS，见第五节）。

**数据说明 —— 先把边界讲清楚，比被问出来强：**

- 单台笔记本（AMD Ryzen 7 7840HS，8C16T，Windows 11），JMeter 与后端**同机**运行，JMeter 已通过 CPU 亲和性钉在后 4 个逻辑核做隔离
- 界面显示的绝对耗时**会随机器状态波动**：同一个方案在不同轮次能差近一倍
- 上面这组是 JMeter 客户端侧数字（用户实际感知的延迟）。**服务端埋点**测到的 P50 仅 **1.9 ms**，两者差值说明绝大部分耗时是排队而非处理
- 结论适用于**方案对比**（同环境同条件），**不代表**绝对 QPS 上限

---

## 二、核心设计

### 下单链路

```
客户端
  │
  ▼
POST /order/seckill/{id}
  │
  ├─ 1. RedisIdWorker.nextId()        ← 一次 INCR，全局唯一订单号
  ├─ 2. Lua 脚本（一次 EVAL 内原子完成）
  │        ├ 库存是否 ≤ 0      → 返回 1，拒绝
  │        ├ 用户是否已购买     → 返回 2，拒绝
  │        ├ INCRBY stock -1
  │        └ SADD   已购用户集合
  ├─ 3. rocketMQTemplate.asyncSend()  ← 非阻塞，立即返回
  │
  ▼
返回订单号（请求线程全程不碰数据库）
  │
  ▼
SeckillOrderConsumer  ──► 条件更新扣减 DB 库存 + 落库
```

### 四层防线

| 层 | 手段 | 解决什么 |
|---|---|---|
| **L1 库存闸门** | Redis + Lua `EVAL` | 检查与扣减在**同一脚本内原子完成**，不存在"先查后改"的竞态窗口 |
| **L2 一人一单** | Redis Set + `SISMEMBER`（同一脚本内） | 与库存校验共享一次 EVAL，不需要额外加锁 |
| **L3 异步解耦** | RocketMQ `asyncSend` + `SendCallback` | 请求线程不做 DB 写入，DB 行锁排队**不再传导到接口耗时** |
| **L4 落库兜底** | `UPDATE ... SET stock = stock - 1 WHERE product_id = ? AND stock > 0` | 乐观锁条件更新。**即使 Redis 层完全失效也不会超卖** |

### 两个关键设计点

**① 为什么库存校验必须和扣减在一起**

朴素的"查库存 → 扣库存"两步在并发下必然超卖。加分布式锁能解决，但锁的粒度决定了吞吐上限。把两个操作写进同一个 Lua 脚本后，Redis 单线程执行保证原子性，**不需要锁**。

**② 为什么用异步落库，以及它的代价**

同步写库时，2000 个请求在同一个商品行上排队抢行锁，接口耗时就等于**排队总时长**——这与代码写得好不好无关，是 DB 单行串行化的物理上限。

代价是：**接口返回成功时订单还没落库**。这引入了新的问题——消息丢了怎么办？当前的取舍是承认这个缺口（见第六节），而不是假装它不存在。

### 消费端幂等

消费者拿到消息后先校验 `runId`：

```java
if (order.getRunId() == null || order.getRunId() != metrics.getRunId()) {
    metrics.consumerStale.increment();
    return;   // 正常返回 = ACK 丢弃；绝不 throw，否则会进重试/DLQ
}
```

`runId` 是每次压测重置时递增的轮次号。它挡住的是**跨轮次的在途消息**——这些消息会对着刚被重置的库存执行 `stock - 1` 和 `INSERT`，**症状与"超卖"一模一样**。

### 同步对照组的实现（`/test/seckill-sync`）

为了做 A/B 对比，保留了一条**刻意的同步写库实现**作为对照组，模拟未优化的朴素做法：

`Redisson 分布式锁 → DB 查重 → 查库存 → 时间窗校验 → 乐观锁扣减 → 同步落库`

它**不用 Redis 做库存预扣**，每一步都直接落 DB，且四条语句是四次独立提交。这条路径不代表任何生产实践，存在的唯一目的是提供可对比的基线。

---

## 三、技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Spring Boot | 2.3.12.RELEASE | 基础框架 |
| MyBatis-Plus | 3.4.3 | ORM |
| MySQL | 8.0 | 订单、商品、用户数据 |
| Redis | 5.0 | 库存预扣、一人一单、分布式锁、缓存、发号器 |
| RocketMQ | 5.2.0 | 订单异步落库、缓存失效通知 |
| Redisson | 3.13.6 | 分布式锁（对照组使用） |
| Hutool | 5.7.17 | 工具类 |
| JMeter | 5.6.3 | 压测 |

---

## 四、快速开始

### 环境要求

- **JDK 11**（注意：**实测 JDK 21 无法启动** —— Spring 5.2 的 CGLIB/ASM 不认 class file v65；
  编译同样受限，Lombok 1.18.20 在 JDK 21 上会报 `NoSuchFieldError: JCTree$JCImport.qualid`）
- MySQL 8.0 / 5.7
- Redis 5.0+
- RocketMQ 5.2.0（Docker 最省事，见下）

### 1. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE hm_ecommerce DEFAULT CHARSET utf8mb4;"
mysql -uroot -p hm_ecommerce < src/main/resources/db/hm_ecommerce.sql
```

### 2. 配置本机参数

数据库和 Redis 密码**不在版本库里**。复制模板并填入自己的值：

```bash
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
```

```yaml
# application-local.yaml —— 该文件已被 .gitignore 排除
spring:
  datasource:
    password: 你的MySQL密码
  redis:
    password: 你的Redis密码
hmdp:
  upload-dir: ./imgs        # 图片上传目录，指向你的 nginx 静态资源目录
```

> `application.yaml` 里配了 `spring.profiles.include: local`，所以这个文件在所有 profile 下都会加载，且优先级高于 `application.yaml`。文件不存在时应用仍能启动，但密码为空会导致连接失败——**报错明确，不会静默连上别的库**。

### 3. 启动 RocketMQ

```bash
docker network create rocketmq

# namesrv
docker run -d --name rmq-namesrv --network rocketmq \
  -p 9876:9876 -e "JAVA_OPT_EXT=-Xms256m -Xmx256m" \
  apache/rocketmq:5.2.0 sh mqnamesrv

# broker.conf —— 关键一行：broker 注册到 namesrv 的地址必须是宿主机能访问到的
echo "brokerIP1=host.docker.internal" > /你的路径/broker.conf

# broker
docker run -d --name rmq-broker --network rocketmq \
  -p 10909:10909 -p 10911:10911 -p 10912:10912 \
  -e "NAMESRV_ADDR=rmq-namesrv:9876" -e "JAVA_OPT_EXT=-Xms512m -Xmx512m" \
  -v /你的路径/broker.conf:/home/rocketmq/rocketmq-5.2.0/conf/broker.conf \
  apache/rocketmq:5.2.0 \
  sh mqbroker -c /home/rocketmq/rocketmq-5.2.0/conf/broker.conf
```

> **`brokerIP1` 是这套配置里最容易踩的坑**：不配的话 broker 会把**容器内网 IP** 注册到 namesrv，宿主机上的应用拿到这个地址后连不上，报 `RemotingConnectException: connect to null failed`。用 `host.docker.internal` 让宿主机可达。

可选 —— RocketMQ 控制台（<http://localhost:8088>）：

```bash
docker run -d --name rmq-console --network rocketmq -p 8088:8082 \
  -e "JAVA_OPTS=-Drocketmq.namesrv.addr=rmq-namesrv:9876" \
  apacherocketmq/rocketmq-dashboard
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

访问 <http://localhost:8081>。

> **压测场景**请额外激活 `loadtest` profile：它关闭了 MyBatis 的 debug 日志（每条 SQL 三行同步控制台写，在 2000 并发下会严重干扰测量），并调整了连接池参数。
> ```bash
> mvn spring-boot:run -Dspring-boot.run.profiles=loadtest
> ```

---

## 五、压测与一致性验证

### 复位与校验接口

| 接口 | 说明 |
|---|---|
| `POST /test/init-seckill/{productId}?stock=N` | **压测前置步骤**。递增 runId → 等在途消息排空 → 清空订单表 → 重置 DB/Redis 库存 → 清零计数器 |
| `GET /test/metrics?productId=1` | 返回延迟分位、计数器、状态快照与**一致性校验结果** |

### 9 项一致性恒等式

`/test/metrics` 把"保证数据一致性"从**断言**变成了**可验证结论**：

```
noOversell                   dbOrderCount <= initialStock
noDuplicateBuyer             dbOrderCount == dbDistinctUsers
async.redisStockAccounting   redisStock + luaAccepted == initialStock
async.redisSetAccounting     scard(purchasedSet) == luaAccepted
async.mqAccounting           luaAccepted == mqSendOk + mqSendFail
async.consumerAccounting     consumerAttempt == consumerSuccess + consumerFailure + consumerStale
async.drained                consumerAttempt == mqSendOk + consumerStale
async.endToEnd               dbOrderCount == consumerSuccess
async.dbStockAccounting      dbStock + consumerSuccess == initialStock && consumerStockExhausted == 0
```

另有派生指标 `asyncUnaccounted = asyncRequests - non2xx - (luaOutOfStock + luaDuplicate + luaAccepted)`，
**非 0 说明有请求绕过了埋点**——这是最廉价的埋点自身 bug 探测器。

### 复现步骤

```bash
# 1. 复位（强制，且必须在压测之前）
curl -X POST "http://localhost:8081/test/init-seckill/1?stock=2000&waitSeconds=60&quietMs=1000"

# 2. 压测
jmeter -n -t seckill-compare.jmx -l results/run.jtl -e -o results/run-report

# 3. 取服务端快照（含 9 项校验）
curl "http://localhost:8081/test/metrics?productId=1&waitSeconds=60"
```

---

## 六、已知局限

这部分是**主动交代**的边界，不是遗留问题清单。

1. **异步发送失败没有补偿** —— 这是当前设计**真实存在的缺口**。
   Broker 背压保护（`TIMEOUT_CLEAN_QUEUE`，默认队列等待上限 200 ms）下，发送可能失败，而 `onException` 里目前只打了一行日志。这意味着：**用户收到 `success: true`、Redis 库存已扣、但订单永远不落库**。
   实测失败率：应用刚重启后的第一轮为 13.1%（冷启动尖峰），稳态下 ≤ 0.05%（7 轮中 6 轮为 0~1 条）。
   生产解法按彻底程度排序：本地消息表 / 事务性发件箱 → 对账补偿 → RocketMQ 事务消息。

2. **`tb_order` 表没有 `product_id` / `user_id` 索引** —— 按建表时的 DDL 现状测量，未做优化。

3. **压测数据来自单机同机测试** —— 适用于对比，不适用于声称绝对性能上限。

4. **Redis 连接池配置实际不生效** —— Boot 默认 `shareNativeConnection=true`，所有命令共用一条多路复用连接。
   曾尝试关闭它以启用连接池，结果**更慢**（每个命令都要向 Apache Commons Pool 借还连接，400 并发下拦截器 Redis 的 P50 从 0.88 ms 恶化到 35 ms）。
   共享连接本身在 400 并发下只需 0.35~0.9 ms/命令，**从来不是瓶颈**。已回退，相关结论写在 `application-loadtest.yaml` 的注释里。

---

## 七、项目结构

```
src/main/java/com/hmdp/
├── controller/          OrderController（秒杀入口）、TestController（压测辅助）等
├── service/impl/
│   └── OrderServiceImpl      ★ 秒杀主链路 seckillProduct / 消费端 createOrder / 同步对照组
├── mq/
│   ├── SeckillOrderConsumer  ★ 订单落库消费者（含 runId 幂等守卫）
│   ├── CacheInvalidationConsumer
│   └── OrderTimeoutConsumer
├── metrics/                  ★ 压测埋点
│   ├── LatencyRecorder       原始纳秒样本 + 最近邻分位
│   ├── SeckillMetrics        全 LongAdder 计数器 + 9 项恒等式
│   └── MetricsInterceptor    HandlerInterceptor，按 URI 分流两条路径
├── config/              MvcConfig（拦截器注册）、RedissonConfig、JacksonConfig 等
├── utils/               RedisIdWorker（发号器）、LoginInterceptor、CacheClient 等
└── ...

src/main/resources/
├── seckill.lua          ★ 库存扣减 + 一人一单（原子）
├── rate_limit.lua       接口限流
├── application.yaml             主配置（密码用占位符）
├── application-local.yaml.example  本机私有配置模板
└── application-loadtest.yaml    压测 profile
```

---

## 八、来源说明

工程底座来自黑马点评（hmdp）教学项目。在此基础上独立完成的部分包括：

- 商品与秒杀模块的数据模型、接口与业务逻辑
- Redis + Lua 原子扣减与一人一单方案
- RocketMQ 异步落库链路与消费端幂等守卫
- 同步对照组实现（`seckillProductSync`）
- 压测埋点体系与 9 项一致性校验
- 单机压测方案设计与性能问题定位
