#!/usr/bin/env bash
# 演示三个核心场景（需先通过 ./start.sh 启动服务）
#   1. 容量不足改用普通杯，价格立即重算
#   2. 两人同时换杯：一人 409，重试后价格与最终选择一致
#   3. 退款后优惠冲回、优惠券恢复可用
set -euo pipefail
BASE="${1:-http://localhost:8080}/api"
BODY=$(mktemp)
trap 'rm -f "$BODY"' EXIT

# JSON 求值：jget "d['pricing']['totalCents']"
jget() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }

# POST：状态码打印到 stdout，响应体写入 $BODY
post() { curl -s -o "$BODY" -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d "$2" "$BASE$1"; }

hr() { echo ""; echo "════════════════════════════════════════════════════════"; echo "  $1"; echo "════════════════════════════════════════════════════════"; }

hr "场景 1：自带杯容量不足 → 登记拒收 + 自动切换普通杯 + 价格重算"
post /orders '{"items":[{"drinkSizeId":2,"quantity":1}]}' >/dev/null   # 大杯美式 650ml ¥22
ORDER=$(cat "$BODY"); OID=$(echo "$ORDER" | jget "d['id']"); V=$(echo "$ORDER" | jget "d['version']")
echo "开单 #$OID：大杯美式 650ml，小计 $(echo "$ORDER" | jget "d['pricing']['subtotalCents']") 分，自带杯需 ≥650ml"

CODE=$(post "/orders/$OID/container/own-cup/verify" "{\"expectedVersion\":$V,\"capacityMl\":500,\"hygiene\":\"CLEAN\",\"inspector\":\"小王\",\"changedBy\":\"小王\"}")
R=$(cat "$BODY")
echo "顾客杯子 500ml 核验 → HTTP $CODE"
echo "  生效容器:   $(echo "$R" | jget "d['container']['type']")（$(echo "$R" | jget "d['container']['note']")）"
echo "  拒收记录:   $(echo "$R" | jget "d['rejections'][0]['reason']")"
echo "  自带杯折扣: $(echo "$R" | jget "d['pricing']['cupDiscountCents']") 分 → 应付 $(echo "$R" | jget "d['pricing']['totalCents']") 分"

hr "场景 2：两名店员同时改杯 → 一人 409 → 重试 → 价格与最终选择一致"
post /orders '{"items":[{"drinkSizeId":1,"quantity":1}]}' >/dev/null   # 中杯美式 ¥18
ORDER=$(cat "$BODY"); OID=$(echo "$ORDER" | jget "d['id']"); V=$(echo "$ORDER" | jget "d['version']")
echo "开单 #$OID：中杯美式，版本 $V。店员甲切普通杯、店员乙核验自带杯，基于同一版本并发提交…"

TMP=$(mktemp -d)
curl -s -o "$TMP/a.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"expectedVersion\":$V,\"changedBy\":\"甲\"}" "$BASE/orders/$OID/container/store-cup" > "$TMP/a.code" &
curl -s -o "$TMP/b.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"expectedVersion\":$V,\"capacityMl\":500,\"hygiene\":\"CLEAN\",\"inspector\":\"乙\",\"changedBy\":\"乙\"}" \
  "$BASE/orders/$OID/container/own-cup/verify" > "$TMP/b.code" &
wait
A=$(cat "$TMP/a.code"); B=$(cat "$TMP/b.code")
echo "  甲(普通杯): HTTP $A    乙(自带杯): HTTP $B"

V=$(curl -s "$BASE/orders/$OID" | jget "d['version']")
if [ "$A" = "409" ]; then
  echo "  甲收到 409，按最新版本 $V 重试…"
  post "/orders/$OID/container/store-cup" "{\"expectedVersion\":$V,\"changedBy\":\"甲\"}" >/dev/null
else
  echo "  乙收到 409，按最新版本 $V 重试…"
  post "/orders/$OID/container/own-cup/verify" "{\"expectedVersion\":$V,\"capacityMl\":500,\"hygiene\":\"CLEAN\",\"inspector\":\"乙\",\"changedBy\":\"乙\"}" >/dev/null
fi
FINAL=$(curl -s "$BASE/orders/$OID")
echo "  最终容器:   $(echo "$FINAL" | jget "d['container']['type']")"
echo "  最终价格:   小计 $(echo "$FINAL" | jget "d['pricing']['subtotalCents']") - 自带杯折扣 $(echo "$FINAL" | jget "d['pricing']['cupDiscountCents']") = $(echo "$FINAL" | jget "d['pricing']['totalCents']") 分"
echo "  生效容器数: $(echo "$FINAL" | jget "len([c for c in d['containerHistory'] if c['active']])")（恒为 1）"
rm -rf "$TMP"

hr "场景 3：支付后退款 → 优惠冲回 + 优惠券恢复可用"
post /orders '{"items":[{"drinkSizeId":1,"quantity":1},{"drinkSizeId":3,"quantity":1}]}' >/dev/null  # 1800+2400
ORDER=$(cat "$BODY"); OID=$(echo "$ORDER" | jget "d['id']"); V=$(echo "$ORDER" | jget "d['version']")
post "/orders/$OID/container/own-cup/verify" "{\"expectedVersion\":$V,\"capacityMl\":500,\"hygiene\":\"CLEAN\",\"inspector\":\"小王\",\"changedBy\":\"小王\"}" >/dev/null
V=$(cat "$BODY" | jget "d['version']")
post "/orders/$OID/coupon" "{\"expectedVersion\":$V,\"code\":\"SAVE5\"}" >/dev/null
R=$(cat "$BODY"); V=$(echo "$R" | jget "d['version']")
echo "开单 #$OID：小计 4200，自带杯 -600，SAVE5 -500 → 应付 $(echo "$R" | jget "d['pricing']['totalCents']") 分"

post "/orders/$OID/pay" "{\"expectedVersion\":$V,\"method\":\"MOCK_QR\"}" >/dev/null
R=$(cat "$BODY")
echo "收款 $(echo "$R" | jget "d['payment']['amountCents']") 分（模拟扫码），SAVE5 状态: $(curl -s "$BASE/coupons" | jget "[c for c in d if c['code']=='SAVE5'][0]['status']")"

post "/orders/$OID/refund" '{"reason":"顾客临时有事"}' >/dev/null
R=$(cat "$BODY")
echo "退款完成：订单 $(echo "$R" | jget "d['status']")，支付单 $(echo "$R" | jget "d['payment']['status']")"
echo "  退回金额:         $(echo "$R" | jget "d['refunds'][0]['amountCents']") 分"
echo "  冲回自带杯优惠:   $(echo "$R" | jget "d['refunds'][0]['reversedCupDiscountCents']") 分"
echo "  冲回优惠券抵扣:   $(echo "$R" | jget "d['refunds'][0]['reversedCouponDiscountCents']") 分"
echo "  SAVE5 状态:       $(curl -s "$BASE/coupons" | jget "[c for c in d if c['code']=='SAVE5'][0]['status']")（恢复可用）"

echo ""
echo "三个场景演示完成。"
