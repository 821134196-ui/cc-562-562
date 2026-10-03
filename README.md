# 自带杯折扣核验与卫生拒收系统

饮品店收银台：点单时核验顾客自带杯（容量 + 卫生），实时计算折扣与最终价格；
拒收自动切换普通杯并留痕；优惠券叠加规则受控；支持并发改杯、支付与退款（均为本地模拟）。

## 一条命令启动

```bash
./start.sh
```

打开 http://localhost:8080 即为收银台（前端由后端同端口托管，SQLite 数据在 `./data/cupcheck.db`，每次启动重建演示数据）。

- `./run-tests.sh` — 运行后端测试（15 个用例）
- `./demo.sh` — 对运行中的服务演示三个核心场景（curl 版）

> 本仓库自带 `.tools/`（JDK 17 + Maven，已被 .gitignore 忽略）；若系统已有 Java 17+ 与 Maven，会直接使用系统版本。

## 技术栈

- **后端**：Java 17 · Quarkus 3（REST + Hibernate ORM）· SQLite（WAL 模式）
- **前端**：Svelte 4 + Vite，构建产物进入 `backend/src/main/resources/META-INF/resources`
- **支付/收银**：全部本地模拟接口，无外部依赖

## 业务规则

**计价**（金额单位：分，避免浮点误差）

- 小计 = Σ(单价 × 数量)
- 自带杯折扣 = 每件「未制作」饮品减 3 元（`app.cup-discount-cents` 可配）
- 优惠券按券面规则抵扣；总折扣不超过小计（不重复抵扣）
- 每次变更后价格快照落库，付款前看到的就是最终价

**自带杯核验**（`POST /api/orders/{id}/container/own-cup/verify`）

- 容量须 ≥ 订单中最大单杯容量；卫生须合格（杯口不洁 / 有残留 / 破损均拒收）
- 通过 → 自带杯生效并立即按折扣重算
- 拒收 → 登记拒收记录（**应用层无修改入口，数据库触发器禁止 UPDATE/DELETE**）并自动切换普通杯，价格立即重算
- 加饮品后若杯子容量不再达标，系统自动判拒并切回普通杯

**换杯与制作状态**

- 同一订单任意时刻只有一条生效容器（部分唯一索引保证），换杯全程留痕
- 饮品「已制作」时锁定当时的自带杯折扣；「未制作」的随换杯实时重算

**优惠券叠加**

- 券面带 `stackableWithCupDiscount` 开关：不可叠加的券在自带杯折扣生效时不可用（422）
- 未实际产生抵扣的券在支付时不核销、不占用

**并发（两人同时改杯）**

- 订单乐观锁：所有改价操作携带 `expectedVersion`，提交时数据库行级版本校验
- 后提交者收到 409，前端自动刷新后重试；最终价格与最终容器选择一致

**退款**

- 整单退款：支付单冲回、订单置为已退款
- 退款单记录冲回的自带杯折扣与优惠券抵扣金额；已核销优惠券恢复为可用，可在新订单再次使用

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/menu` | 饮品与规格（容量/价格） |
| GET | `/api/rules` | 折扣规则与门店信息 |
| GET | `/api/coupons` | 优惠券列表（含叠加规则） |
| POST | `/api/orders` | 开单 |
| GET | `/api/orders` / `/api/orders/{id}` | 订单列表 / 详情（含价格明细） |
| POST | `/api/orders/{id}/items` | 加购饮品 |
| POST | `/api/orders/{id}/container/own-cup/verify` | 自带杯核验（容量+卫生） |
| POST | `/api/orders/{id}/container/store-cup` | 切换普通杯 |
| GET | `/api/orders/{id}/rejections` | 拒收记录（只读，无删除入口） |
| POST | `/api/orders/{id}/coupon` · `/coupon/remove` | 用券 / 撤券 |
| POST | `/api/orders/{id}/items/{itemId}/made` | 标记已制作（锁定折扣） |
| POST | `/api/orders/{id}/pay` | 收款（本地模拟） |
| POST | `/api/orders/{id}/refund` | 退款（本地模拟） |

错误格式：`{"code": "...", "message": "..."}`；409 = 版本冲突（刷新重试），422 = 业务规则不满足。

## 测试场景（`backend/src/test/java/com/cupcheck`）

- `CupVerificationTest` — 容量不足改普通杯、杯口不洁拒收、核验通过打折、换杯留痕、已制作/未制作差异计价、已支付禁改
- `ConcurrentCupChangeTest` — 并发换杯一人 409、重试后价格与最终选择一致、过期版本拒绝
- `RefundTest` — 退款冲回优惠、优惠券恢复可用、重复退款拒绝
- `CouponStackingTest` — 叠加规则互斥/同享、最低消费、折扣不超小计
- `RejectionImmutabilityTest` — 数据库触发器拒绝删除/篡改拒收记录

## 目录结构

```
├── start.sh / run-tests.sh / demo.sh
├── backend/          Quarkus + Hibernate ORM + SQLite
│   └── src/main/java/com/cupcheck/
│       ├── model/    实体（Order 乐观锁、ContainerSelection、CupInspection、RejectionRecord…）
│       ├── service/  PricingService（计价）、OrderService（事务操作）、SchemaGuards（触发器）
│       └── api/      REST 资源、DTO、异常映射
└── frontend/         Svelte 收银台（菜单 / 订单 / 容器核验 / 优惠券 / 支付退款）
```
