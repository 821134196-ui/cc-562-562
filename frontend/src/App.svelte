<script>
  import { onMount } from 'svelte';
  import { api, yuan } from './api.js';

  let beverages = [];
  let coupons = [];
  let order = null;
  let busy = false;
  let error = '';
  let conflict = false;

  // 新单表单
  let newBeverageId = '';
  let quantity = 1;
  let usePersonal = false;
  let newCupMl = 400;

  // 核验表单
  let cupMl = 400;
  let rimClean = true;
  let rejectNote = '';

  // 券 / 载入订单
  let couponCode = '';
  let loadId = '';

  const STATUS_LABEL = {
    CREATED: ['未制作', 'gray'], PREPARED: ['已制作·锁定', 'amber'],
    PAID: ['已支付', 'green'], REFUNDED: ['已退款', 'blue']
  };
  const CHECK_LABEL = {
    PENDING: ['待核验', 'amber'], PASSED: ['核验通过', 'green'],
    REJECTED: ['核验不通过', 'red'], NOT_REQUIRED: ['普通杯免核验', 'gray']
  };
  const REASON_LABEL = {
    RIM_UNSANITARY: '杯口不洁',
    INSUFFICIENT_CAPACITY: '容量不足'
  };
  const EVENT_LABEL = {
    CREATED: '创建订单', CHECK_PASSED: '核验通过', RIM_REJECTED: '杯口不洁拒收',
    CAPACITY_REJECTED: '容量不足拒收', SWITCH_TO_DISPOSABLE: '改用普通杯',
    BLOCKED_AFTER_PREPARED: '已制作·换杯被拒', MARK_PREPARED: '制作完成',
    COUPON_APPLIED: '应用优惠券', COUPON_REMOVED: '移除优惠券',
    COUPON_AUTO_REMOVED_BY_RULE: '规则摘下优惠券', PAID: '支付',
    REFUNDED: '退款', COUPON_REVERTED_BY_REFUND: '退款冲回优惠券'
  };

  onMount(loadCatalog);

  async function loadCatalog() {
    [beverages, coupons] = await Promise.all([api.listBeverages(), api.listCoupons()]);
    if (!newBeverageId && beverages[0]) {
      newBeverageId = beverages[0].id;
      newCupMl = beverages[0].sizeMl;
    }
  }

  async function run(fn) {
    busy = true; error = ''; conflict = false;
    try {
      order = await fn();
      return order;
    } catch (e) {
      if (e.versionConflict) {
        conflict = true;
        error = e.message;
      } else {
        error = e.message;
      }
    } finally {
      busy = false;
    }
  }

  async function createOrder() {
    const payload = {
      beverageId: Number(newBeverageId),
      quantity: Number(quantity),
      personalCupMl: usePersonal ? Number(newCupMl) : null
    };
    const o = await run(() => api.createOrder(payload));
    if (o) cupMl = o.personalCupMl || o.requiredMl;
  }

  async function refreshOrder() {
    if (!order) return;
    await run(() => api.getOrder(order.id));
    conflict = false;
  }

  async function loadExisting() {
    if (!loadId) return;
    await run(() => api.getOrder(Number(loadId)));
  }

  // 提交杯具核验 = 声明/更换为自带杯并核验；
  // 后端按容量与杯口自动判定：通过享折扣；容量不足/杯口不洁则登记拒收并切普通杯。
  function submitInspect() {
    return run(() => api.switchCup(order.id, {
      version: order.version,
      target: 'PERSONAL',
      personalCupMl: Number(cupMl),
      rimClean,
      note: rejectNote || null
    }));
  }

  function switchDisposable() {
    return run(() => api.switchCup(order.id, { version: order.version, target: 'DISPOSABLE' }));
  }

  function applyCoupon() {
    if (!couponCode) return;
    return run(() => api.applyCoupon(order.id, { version: order.version, code: couponCode }));
  }
  function removeCoupon() {
    return run(() => api.removeCoupon(order.id, order.version));
  }
  const prepare = () => run(() => api.prepare(order.id, order.version));
  const pay = () => run(() => api.pay(order.id, order.version));
  const refund = () => run(() => api.refund(order.id, order.version));

  $: editable = order?.status === 'CREATED';
  $: currentBeverage = beverages.find(b => b.id === order?.beverage?.id);
  $: if (currentBeverage && (!cupMl || cupMl === 0)) cupMl = currentBeverage.sizeMl;
  $: capacityPreviewOk = !order ? true : Number(cupMl) >= order.requiredMl;
</script>

<svelte:head><title>自带杯折扣核验 · 收银点单</title></svelte:head>

<div class="wrap">
  <div class="topbar">
    <h1>🥤 自带杯折扣核验与卫生拒收</h1>
    <span class="sub">收银点单 · 本地模拟支付 · 自带杯立减 {order?.personalDiscountPercent ?? 5}%</span>
  </div>

  {#if conflict}
    <div class="conflict">
      <div><b>⚠ 数据已被另一位店员修改：</b>{error}</div>
      <button class="ghost" on:click={refreshOrder}>刷新为最新状态</button>
    </div>
  {:else if error}
    <div class="error">❌ {error}</div>
  {/if}

  <div class="grid">
    <!-- 左列：开单 + 杯具核验 -->
    <div class="card">
      <h2>① 新建订单</h2>
      <label class="fld">
        <span>饮品规格</span>
        <select bind:value={newBeverageId}>
          {#each beverages as b}
            <option value={b.id}>{b.name} · {b.sizeName}（{b.sizeMl}ml）¥{yuan(b.basePriceCents)}</option>
          {/each}
        </select>
      </label>
      <div class="row">
        <label class="fld grow">
          <span>数量</span>
          <input type="number" min="1" bind:value={quantity} />
        </label>
        <label class="fld grow">
          <span>顾客带杯</span>
          <div class="row" style="padding-top:8px">
            <input type="checkbox" bind:checked={usePersonal} id="up" />
            <label for="up" style="margin:0">使用自带杯（先核验后享折扣）</label>
          </div>
        </label>
      </div>
      {#if usePersonal}
        <label class="fld">
          <span>自带杯标称容量 (ml)</span>
          <input type="number" bind:value={newCupMl} />
        </label>
      {/if}
      <button on:click={createOrder} disabled={busy || !beverages.length}>开单</button>
      <span class="note" style="margin-left:10px">也可输入订单号载入：
        <input style="width:90px;display:inline-block" type="number" bind:value={loadId} placeholder="订单号" />
        <button class="gray" on:click={loadExisting} disabled={busy}>载入</button>
        （可开两个浏览器标签载入同一订单，模拟两名店员同时改杯）
      </span>
    </div>

    <!-- 右列：订单与价格 -->
    {#if order}
      <div class="card">
        <h2>
          <span>② 订单明细 <span class="ver">#{order.id} · 版本 v{order.version}</span></span>
          <span class="badge {STATUS_LABEL[order.status][1]}">{STATUS_LABEL[order.status][0]}</span>
        </h2>

        <dl class="kv">
          <dt>饮品规格</dt><dd>{order.beverage.name} · {order.beverage.sizeName}</dd>
          <dt>规格所需容量</dt><dd>{order.requiredMl} ml（杯子容量小于此值判容量不足）</dd>
          <dt>数量</dt><dd>{order.quantity} 杯</dd>
          <dt>当前容器</dt>
          <dd>
            <span class="badge {order.containerType === 'PERSONAL' ? 'blue' : 'gray'}">
              {order.containerType === 'PERSONAL' ? '顾客自带杯' : '门店普通杯'}
            </span>
            {#if order.personalCupMl != null}<span class="muted"> · {order.personalCupMl}ml</span>{/if}
          </dd>
          <dt>卫生检查</dt>
          <dd><span class="badge {CHECK_LABEL[order.checkStatus][1]}">{CHECK_LABEL[order.checkStatus][0]}</span></dd>
          <dt>自带杯折扣</dt>
          <dd>
            {#if order.personalDiscountActive}
              <span class="badge green">已享 {order.personalDiscountPercent}% 折扣</span>
            {:else}
              <span class="muted">未生效（需合格自带杯且核验通过）</span>
            {/if}
          </dd>
          <dt>优惠券</dt>
          <dd>
            {#if order.coupon}
              {order.coupon.code} · {order.coupon.description}
              {#if editable}
                <button class="gray" style="padding:2px 8px;margin-left:8px" on:click={removeCoupon} disabled={busy}>移除</button>
              {/if}
            {:else}<span class="muted">无</span>{/if}
          </dd>
        </dl>

        <div style="margin-top:12px">
          <div class="price-line"><span>基础金额</span><span class="v">¥{yuan(order.price.baseTotalCents)}</span></div>
          <div class="price-line discount">
            <span>自带杯折扣 -{order.personalDiscountPercent}%</span>
            <span class="v">-¥{yuan(order.price.personalDiscountCents)}</span>
          </div>
          <div class="price-line discount">
            <span>优惠券抵扣</span><span class="v">-¥{yuan(order.price.couponDiscountCents)}</span>
          </div>
          <div class="price-final">
            <span>顾客应付（付款前价格）</span>
            <span class="big">¥{yuan(order.price.finalPriceCents)}</span>
          </div>
        </div>
      </div>

      <!-- 杯具核验 -->
      <div class="card">
        <h2>③ 杯具核验 / 中途换杯</h2>
        {#if editable}
          <div class="row">
            <label class="fld grow">
              <span>杯子容量 (ml)</span>
              <input type="number" bind:value={cupMl} class:invalid={!capacityPreviewOk} />
            </label>
            <label class="fld grow">
              <span>杯口卫生</span>
              <div class="row" style="padding-top:8px">
                <input type="checkbox" bind:checked={rimClean} id="rim" />
                <label for="rim" style="margin:0">杯口洁净无污渍</label>
              </div>
            </label>
          </div>
          <label class="fld">
            <span>拒收备注（可选，将随拒收记录永久留存）</span>
            <input bind:value={rejectNote} placeholder="如：杯口有口红印 / 杯子只有 300ml" />
          </label>
          {#if !capacityPreviewOk}
            <p class="note" style="color:var(--red)">⚠ 杯容量 {cupMl}ml 小于规格要求 {order.requiredMl}ml，提交后将登记「容量不足」拒收并自动切换普通杯。</p>
          {/if}
          <div class="row">
            <button on:click={submitInspect} disabled={busy}>提交核验（更换为自带杯）</button>
            <button class="ghost" on:click={switchDisposable} disabled={busy}>改用普通杯</button>
          </div>
          <p class="note">核验通过立即按自带杯重算价格；杯口不洁或容量不足会登记拒收记录（不可删除）并切回普通杯重算。</p>
        {:else}
          <p class="lock-note">🔒 订单状态「{STATUS_LABEL[order.status][0]}」，饮品已制作/完结，容器与价格已锁定，换杯将被拒绝并留痕。</p>
        {/if}
      </div>

      <!-- 优惠券 + 支付 -->
      <div class="card">
        <h2>④ 优惠券与收银</h2>
        {#if editable}
          <div class="row" style="margin-bottom:10px">
            <select class="grow" bind:value={couponCode}>
              <option value="">选择优惠券…</option>
              {#each coupons as c}
                <option value={c.code} disabled={c.status === 'USED'}>
                  {c.code} — {c.description}{c.status === 'USED' ? '（已使用）' : ''}
                </option>
              {/each}
            </select>
            <button class="ghost" on:click={applyCoupon} disabled={busy || !couponCode}>应用</button>
          </div>
          <p class="hint">叠加规则：折扣券(NEW10/VIP15)与自带杯折扣<b>不可叠加</b>——核验通过时会被自动摘下并恢复可用；立减券(SAVE200)可叠加，但按折后价计算，不会重复抵扣。券一经应用即占用，其他订单无法重复使用。</p>
          <div class="row" style="margin-top:12px">
            <button class="gray" on:click={prepare} disabled={busy}>标记饮品制作完成</button>
            <button on:click={pay} disabled={busy}>本地模拟收款 ¥{yuan(order.price.finalPriceCents)}</button>
          </div>
        {:else if order.status === 'PREPARED'}
          <button on:click={pay} disabled={busy}>本地模拟收款 ¥{yuan(order.price.finalPriceCents)}</button>
        {:else if order.status === 'PAID'}
          <button class="danger" on:click={refund} disabled={busy}>本地模拟退款（冲回优惠券）</button>
          <p class="note">支付单号 {order.paymentRef}</p>
        {:else if order.status === 'REFUNDED'}
          <p><span class="badge blue">已退款</span>
            <span class="note">退款单号 {order.refundRef}；若订单使用了优惠券，已冲回为未使用。</span></p>
        {/if}
      </div>

      <!-- 拒收记录 -->
      <div class="card full">
        <h2>⑤ 卫生拒收记录（仅可登记，店员无权删除）</h2>
        {#if order.rejections.length === 0}
          <p class="muted">暂无拒收记录。</p>
        {:else}
          <table>
            <thead><tr><th>#</th><th>时间</th><th>拒收原因</th><th>杯容量</th><th>规格要求</th><th>备注</th></tr></thead>
            <tbody>
              {#each order.rejections as r}
                <tr>
                  <td>{r.id}</td>
                  <td>{new Date(r.createdAt).toLocaleString('zh-CN')}</td>
                  <td><span class="badge red">{REASON_LABEL[r.reason]}</span></td>
                  <td>{r.cupMl}ml</td>
                  <td>{r.requiredMl}ml</td>
                  <td>{r.note || '—'}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        {/if}
      </div>

      <!-- 时间线 -->
      <div class="card full">
        <h2>⑥ 订单变化时间线（换杯 / 拒收 / 券 / 支付 / 退款）</h2>
        <ul class="timeline">
          {#each [...order.events].reverse() as e}
            <li>
              <span class="t">{new Date(e.createdAt).toLocaleString('zh-CN')}</span>
              <span class="a">{EVENT_LABEL[e.action] || e.action}</span>
              <span>{e.detail}</span>
            </li>
          {/each}
        </ul>
      </div>
    {/if}
  </div>
</div>

<style>
  .invalid { border-color: var(--red); }
</style>
