# 自带杯折扣核验与卫生拒收系统

饮品店收银点单系统：收银员在顾客付款前确认自带杯是否适用（容量 + 杯口卫生），
系统即时计算优惠与最终价格；杯口不洁或容量不足时登记**不可删除**的拒收记录并
自动切换普通杯；支持顾客中途换杯、优惠券叠加规则、两人同时改杯的并发一致性，
以及退款后的优惠冲回。收银与支付全部**本地模拟**，数据库使用本地 SQLite 文件。

- 后端：Java 17 + Quarkus 3 + Hibernate ORM（RESTEasy Reactive + Jackson）
- 前端：Svelte 4 + Vite（构建产物由 Quarkus 同源提供）
- 数据库：SQLite（xerial 驱动 + Hibernate 社区方言，WAL 模式）

## 一条命令启动

前置：JDK 17、Maven 3.8+、Node 18+（首次构建前端需要）。

```bash
./run.sh
```

打开 http://localhost:8080 即是收银点单页面。SQLite 文件默认是项目根目录的
`pos.db`，可用 `POS_DB_PATH=/tmp/x.db ./run.sh` 更换位置。首次运行会自动写入
演示饮品规格与优惠券：

| 饮品 | 规格 | 所需容量 | 单价 |
|---|---|---|---|
| 美式咖啡 | 中杯 / 大杯 | 360 / 500 ml | ¥18 / ¥22 |
| 燕麦拿铁 | 中杯 / 大杯 | 400 / 550 ml | ¥24 / ¥28 |
| 满杯鲜果茶 | 大杯 | 600 ml | ¥26 |

优惠券：`NEW10`（9 折，**不可**与自带杯叠加）、`VIP15`（85 折，不可叠加）、
`SAVE200`（立减 ¥2，可叠加）。自带杯折扣默认 5%（`pos.pricing.personal-discount-percent`）。

端到端示例（另开终端，服务启动后执行）：

```bash
python3 examples/demo.py
```

覆盖三个必看场景：**容量不足改用普通杯**、**并发换杯重新计价**、**退款后的优惠处理**。

## 页面操作说明

1. **新建订单**：选饮品规格、数量，可勾选顾客自带杯（此时仅登记，未核验不享折扣）。
2. **杯具核验**：录入杯子容量（ml）和杯口是否洁净后提交：
   - 容量 < 规格要求 → 登记 `INSUFFICIENT_CAPACITY` 拒收，自动切普通杯并重算；
   - 杯口不洁 → 登记 `RIM_UNSANITARY` 拒收，自动切普通杯并重算；
   - 合格 → 核验通过，价格按自带杯折扣重算。
3. **中途换杯**：未制作可随时换；「标记饮品制作完成」后容器与价格锁定，
   换杯被拒绝并在时间线留下 `BLOCKED_AFTER_PREPARED` 记录。
4. **优惠券**：下拉选择并应用；应用即占用。合格自带杯订单上应用不可叠加券会被拒；
   先挂不可叠加券再核验合格自带杯，券会被自动摘下并恢复为未使用。
5. **支付 / 退款**：本地模拟生成支付/退款单号；退款后已占用的券冲回为未使用。
6. 页面同时展示：饮品规格、杯容量、卫生检查结果、价格明细、拒收记录、变化时间线、
   当前版本号。开两个浏览器标签载入同一订单即可模拟两名店员同时改杯：后提交者看到
   冲突提示，刷新后基于最新价格重试。

## 关键业务规则

- **同一订单只有一个有效容器**：`pos_order.container_type` 唯一表示当前选择；
  拒收/换杯是“覆盖式”切换，历史变化全部进入 append-only 时间线 `order_event`。
- **卫生拒收不可删除**：`hygiene_rejection` 只提供新增接口，没有任何修改/删除 API；
  记录拒收原因、杯容量、规格要求容量、备注、时间，换杯/退款后仍然保留。
- **整体重算**：任何容器/券变化都由 `PricingService` 从当前状态整体重算，
  价格永远与状态一致。自带杯折后再算券折扣，顺序作用、折扣下限为 0，不重复抵扣。
  能否叠加由每张券的 `stackable_with_personal` 规则位控制。
- **券不能重复抵扣**：应用即 `USED` 并记录占用订单；其他订单再用返回 409。
  退款冲回 `UNUSED`；因叠加规则被自动摘下时也立即恢复。
- **并发一致性**：订单带 JPA `@Version` 乐观锁。写请求必须携带客户端看到的版本：
  版本过期立即 409（`VERSION_CONFLICT`），数据库层乐观锁再兜底。两人同时改杯后
  数据库只保留一个容器，价格与之一致，失败者刷新重试即可。
- **已制作/未制作不同规则**：`CREATED` 可自由换杯改券；`PREPARED`/`PAID` 容器
  锁定（拒绝换杯且独立事务留痕）；`PAID` 可退款并冲回优惠。

## HTTP API

| 方法 & 路径 | 说明 |
|---|---|
| `GET /api/beverages` / `GET /api/coupons` | 规格与券目录 |
| `POST /api/orders` | 开单 `{beverageId, quantity, personalCupMl?}` |
| `GET /api/orders/{id}` | 订单完整视图（含版本、价格、拒收、时间线） |
| `POST /api/orders/{id}/inspect` | 自带杯核验 `{version, cupMl, rimClean, note?}` |
| `POST /api/orders/{id}/switch-cup` | 换杯 `{version, target, personalCupMl?, rimClean?, note?}` |
| `POST /api/orders/{id}/coupon` / `/coupon/remove` | 应用 / 移除优惠券 |
| `POST /api/orders/{id}/prepare` | 标记制作完成（锁定容器） |
| `POST /api/orders/{id}/pay` / `/refund` | 本地模拟支付 / 退款（冲回券） |

写请求均需带 `version`；冲突返回 `409 {"code":"VERSION_CONFLICT",...}`，
其他业务违反返回 409 与对应 code（`ORDER_LOCKED`、`COUPON_IN_USE`、
`COUPON_NOT_STACKABLE` 等）。

## 开发与测试

```bash
# 后端测试（11 个 @QuarkusTest，文件型 SQLite: target/test-pos.db）
mvn test

# 前端热更新（一个终端后端，一个终端前端）
mvn quarkus:dev                       # 8080
cd frontend && npm install && npm run dev   # 5173，/api 代理到 8080
```

项目布局：

```
src/main/java/com/food/cup/
  domain/    枚举与 JPA 实体（Beverage / PosOrder / Coupon / HygieneRejection / OrderEvent）
  service/   PricingService（计价）、OrderService（业务规则）、AuditService（独立事务留痕）
  repo/      Repository
  web/       REST 资源、异常映射、DTO
  bootstrap/ DataInitializer（SQLite PRAGMA + 演示数据）
frontend/    Svelte 点单页面
examples/    demo.py 端到端示例
```
