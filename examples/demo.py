#!/usr/bin/env python3
"""
自带杯折扣核验与卫生拒收系统 —— 端到端示例脚本（收银/支付均为本地模拟）

用法：先启动服务（./run.sh），再运行：
    python3 examples/demo.py
    BASE=http://localhost:8080 python3 examples/demo.py

覆盖三个必看场景：
  1. 容量不足 -> 登记拒收 -> 自动改用普通杯 -> 重算价格 -> 再换合格杯享折扣
  2. 两名店员同时换杯：一人成功、一人 409，刷新后基于最新价格重试，最终状态一致
  3. 优惠券占用 / 退款后冲回恢复 / 叠加规则（不重复抵扣）
"""
import json
import os
import sys
import threading
import urllib.error
import urllib.request

BASE = os.environ.get("BASE", "http://localhost:8080")


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def yuan(cents):
    return f"¥{cents / 100:.2f}"


def line(t=""):
    print(t)


def banner(n, title):
    line("\n" + "=" * 72)
    print(f"场景 {n}：{title}")
    print("=" * 72)


def price_summary(o, prefix=""):
    p = o["price"]
    print(f"{prefix}容器={container(o)}  卫生={o['checkStatus']}  "
          f"基础 {yuan(p['baseTotalCents'])} - 自带杯折 {yuan(p['personalDiscountCents'])} "
          f"- 券 {yuan(p['couponDiscountCents'])} => 应付 {yuan(p['finalPriceCents'])} "
          f"(v{o['version']})")


def container(o):
    if o["containerType"] == "PERSONAL":
        return f"自带杯{o['personalCupMl']}ml"
    return "普通杯"


def create_order(beverage_id, cup_ml=None):
    s, o = call("POST", "/api/orders",
                {"beverageId": beverage_id, "quantity": 1, "personalCupMl": cup_ml})
    assert s == 200, o
    return o


def scenario_1_insufficient_capacity():
    banner(1, "容量不足 -> 拒收登记 -> 改用普通杯重算 -> 换合格杯享折扣")
    # 大杯燕麦拿铁：要求 550ml，单价 28.00 元
    s, drinks = call("GET", "/api/beverages")
    latte = next(d for d in drinks if d["name"] == "燕麦拿铁" and d["sizeName"] == "大杯")
    print(f"点单：{latte['name']}·{latte['sizeName']}，规格要求 {latte['sizeMl']}ml，{yuan(latte['basePriceCents'])}")

    o = create_order(latte["id"], cup_ml=400)  # 顾客递上 400ml 的杯子
    print(f"顾客递来 400ml 自带杯，开单待核验……")
    price_summary(o, "  ")

    # 店员核验：杯口干净，但容量不足
    s, o = call("POST", f"/api/orders/{o['id']}/switch-cup", {
        "version": o["version"], "target": "PERSONAL",
        "personalCupMl": 400, "rimClean": True, "note": "顾客自带保温杯只有400ml"})
    print(f"\n核验结果 HTTP {s}：400ml < {latte['sizeMl']}ml，判定【容量不足】")
    print(f"  -> 已登记拒收记录（不可删除）：{[r['reason'] for r in o['rejections']]}")
    print(f"  -> 唯一有效容器自动切换为：{container(o)}")
    price_summary(o, "  ")
    assert o["containerType"] == "DISPOSABLE"
    assert o["price"]["finalPriceCents"] == 2800

    # 顾客换了一个 600ml 的干净杯子
    s, o = call("POST", f"/api/orders/{o['id']}/switch-cup", {
        "version": o["version"], "target": "PERSONAL",
        "personalCupMl": 600, "rimClean": True})
    print(f"\n顾客改拿 600ml 干净杯，重新核验通过，立即重算：")
    price_summary(o, "  ")
    print(f"  自带杯 5% 折扣 = {yuan(o['price']['personalDiscountCents'])}，"
          f"拒收记录仍保留 {len(o['rejections'])} 条（店员无权删除）")
    assert o["containerType"] == "PERSONAL"
    assert o["price"]["finalPriceCents"] == 2660
    print("✅ 容量不足拒收、普通杯切换、合格杯折扣与重算均正确")


def scenario_2_concurrent_switch():
    banner(2, "两名店员同时换杯 -> 409 冲突 -> 刷新后重试 -> 最终选择与价格一致")
    s, drinks = call("GET", "/api/beverages")
    latte = next(d for d in drinks if d["name"] == "燕麦拿铁" and d["sizeName"] == "大杯")
    o = create_order(latte["id"], cup_ml=600)
    s, o = call("POST", f"/api/orders/{o['id']}/switch-cup", {
        "version": o["version"], "target": "PERSONAL",
        "personalCupMl": 600, "rimClean": True})
    oid = o["id"]
    print(f"订单 #{oid} 当前是合格自带杯 600ml，应付 {yuan(o['price']['finalPriceCents'])}（v{o['version']}）")

    shared_version = o["version"]
    results = {}
    barrier = threading.Barrier(2)

    def clerk(name, target, cup=None):
        barrier.wait()  # 尽量同时提交
        body = {"version": shared_version, "target": target}
        if cup:
            body.update(personalCupMl=cup, rimClean=True)
        results[name] = call("POST", f"/api/orders/{oid}/switch-cup", body)

    t1 = threading.Thread(target=clerk, args=("店员A(改普通杯)", "DISPOSABLE"))
    t2 = threading.Thread(target=clerk, args=("店员B(换700ml自带杯)", "PERSONAL", 700))
    print("\n两名店员同时基于同一版本提交不同换杯请求……")
    t1.start(); t2.start(); t1.join(); t2.join()

    for name, (code, body) in results.items():
        if code == 200:
            print(f"  {name}: HTTP 200 成功 -> {container(body)}，应付 {yuan(body['price']['finalPriceCents'])}")
        else:
            print(f"  {name}: HTTP {code} 被拒 [{body['code']}] {body['message']}")

    s, latest = call("GET", f"/api/orders/{oid}")
    print(f"\n数据库最终只有一个容器选择：{container(latest)}（v{latest['version']}），"
          f"应付 {yuan(latest['price']['finalPriceCents'])}")

    # 失败方刷新后用最新版本重试
    print("失败店员点击【刷新为最新状态】后重试自己的操作……")
    if results["店员A(改普通杯)"][0] != 200:
        body = {"version": latest["version"], "target": "DISPOSABLE"}
    else:
        body = {"version": latest["version"], "target": "PERSONAL",
                "personalCupMl": 700, "rimClean": True}
    s, retry = call("POST", f"/api/orders/{oid}/switch-cup", body)
    print(f"  重试 HTTP {s} -> {container(retry)}，应付 {yuan(retry['price']['finalPriceCents'])}（v{retry['version']}）")
    assert s == 200
    print("✅ 乐观锁 + 版本校验保证并发改杯后价格与最终选择一致")


def scenario_3_refund_coupon():
    banner(3, "优惠券占用与叠加规则 -> 支付 -> 退款 -> 优惠冲回恢复")
    s, drinks = call("GET", "/api/beverages")
    americano = next(d for d in drinks if d["name"] == "美式咖啡" and d["sizeName"] == "大杯")  # 22.00

    # 3.1 不可叠加券：先用券，后核验合格自带杯 -> 券被规则摘下并恢复
    o = create_order(americano["id"])
    print(f"订单A #{o['id']} 普通杯开单 {yuan(o['price']['finalPriceCents'])}")
    s, o = call("POST", f"/api/orders/{o['id']}/coupon",
                {"version": o["version"], "code": "NEW10"})
    print(f"应用 NEW10（9 折，不可与自带杯叠加）-> 应付 {yuan(o['price']['finalPriceCents'])}")
    s, o = call("POST", f"/api/orders/{o['id']}/switch-cup", {
        "version": o["version"], "target": "PERSONAL",
        "personalCupMl": 500, "rimClean": True})
    print(f"顾客换成合格自带杯并核验通过：NEW10 被规则自动摘下恢复，只保留自带杯折扣")
    price_summary(o, "  ")
    assert o["coupon"] is None and o["price"]["finalPriceCents"] == 2090

    # 3.2 可叠加券：按折后价顺序计算，不重复抵扣
    s, o = call("POST", f"/api/orders/{o['id']}/coupon",
                {"version": o["version"], "code": "SAVE200"})
    print(f"\n改应用 SAVE200（立减2元，可叠加）：自带杯折 {yuan(o['price']['personalDiscountCents'])} "
          f"+ 券 {yuan(o['price']['couponDiscountCents'])}，顺序作用于折后价，不重复抵扣")
    price_summary(o, "  ")
    assert o["price"]["finalPriceCents"] == 1890

    # 3.3 占用期间别的订单不能重复抵扣
    other = create_order(americano["id"])
    s, err = call("POST", f"/api/orders/{other['id']}/coupon",
                  {"version": other["version"], "code": "SAVE200"})
    print(f"\nSAVE200 已被订单A占用，订单B 再用 -> HTTP {s} [{err['code']}]")
    assert s == 409

    # 3.4 支付（本地模拟）后退款，券冲回恢复，订单B可再用
    s, paid = call("POST", f"/api/orders/{o['id']}/pay", {"version": o["version"]})
    print(f"订单A 本地模拟支付成功：{paid['paymentRef']}")
    s, refunded = call("POST", f"/api/orders/{paid['id']}/refund", {"version": paid["version"]})
    print(f"订单A 本地模拟退款成功：{refunded['refundRef']}")
    print(f"  时间线末两条：{[e['action'] for e in refunded['events'][-2:]]}")

    s, cs = call("GET", "/api/coupons")
    save = next(c for c in cs if c["code"] == "SAVE200")
    print(f"  SAVE200 当前状态：{save['status']}（退款已冲回）")
    assert save["status"] == "UNUSED"

    s, reused = call("POST", f"/api/orders/{other['id']}/coupon",
                     {"version": other["version"], "code": "SAVE200"})
    print(f"订单B 再次使用 SAVE200 -> HTTP {s}，抵扣 {yuan(reused['price']['couponDiscountCents'])}，"
          f"应付 {yuan(reused['price']['finalPriceCents'])}")
    assert s == 200
    # 清理：退款订单B，恢复演示数据
    call("POST", f"/api/orders/{reused['id']}/pay", {"version": reused["version"]})
    s, final = call("GET", f"/api/orders/{reused['id']}")
    call("POST", f"/api/orders/{reused['id']}/refund", {"version": final["version"]})
    print("✅ 券不能重复抵扣、叠加受规则控制、退款后优惠正确冲回")


def main():
    try:
        call("GET", "/api/beverages")
    except Exception as e:
        print(f"无法连接 {BASE}：{e}\n请先运行 ./run.sh 启动服务。")
        sys.exit(1)
    scenario_1_insufficient_capacity()
    scenario_2_concurrent_switch()
    scenario_3_refund_coupon()
    line("\n" + "=" * 72)
    print("三个示例场景全部通过。打开 http://localhost:8080 可在收银页面手动操作。")
    print("=" * 72)


if __name__ == "__main__":
    main()
