<script>
  import { api } from './api.js'
  import ContainerCard from './ContainerCard.svelte'
  import CouponCard from './CouponCard.svelte'

  export let order
  export let rules
  export let coupons
  export let operator
  export let fmt
  export let run

  let payMethod = 'MOCK_CASH'
  let refundReason = ''
  let refunding = false

  $: open = order.status === 'OPEN'
  $: paid = order.status === 'PAID'
  $: refunded = order.status === 'REFUNDED'
  $: p = order.pricing

  const statusLabel = { OPEN: '点单中', PAID: '已支付', REFUNDED: '已退款' }
</script>

<div class="order-panel">
  <div class="order-head">
    <h2>订单 #{order.id}</h2>
    <span class="badge badge-{order.status.toLowerCase()}">{statusLabel[order.status]}</span>
    <span class="muted">v{order.version}</span>
    {#if order.requiredCapacityMl > 0}
      <span class="muted">自带杯需 ≥ {order.requiredCapacityMl}ml</span>
    {/if}
  </div>

  <!-- 饮品明细：规格 / 状态 / 每件自带杯折扣 -->
  <table class="items">
    <thead>
      <tr><th>饮品</th><th>规格</th><th>数量</th><th>单价</th><th>状态</th><th>自带杯优惠</th><th></th></tr>
    </thead>
    <tbody>
      {#each order.items as item (item.id)}
        <tr>
          <td>{item.drinkName}</td>
          <td>{item.sizeName} · {item.volumeMl}ml</td>
          <td>×{item.quantity}</td>
          <td>{fmt(item.unitPriceCents)}</td>
          <td>
            {#if item.status === 'MADE'}
              <span class="badge badge-made">已制作</span>
            {:else}
              <span class="badge badge-pending">待制作</span>
            {/if}
          </td>
          <td class:zero={item.cupDiscountCents === 0}>
            {item.cupDiscountCents > 0 ? '-' + fmt(item.cupDiscountCents) : '—'}
            {#if item.status === 'MADE' && item.cupDiscountCents > 0}<span class="muted">（已锁定）</span>{/if}
          </td>
          <td>
            {#if item.status === 'PENDING' && !refunded}
              <button on:click={() => run(() => api.markMade(order.id, item.id, order.version), '已标记制作')}>
                标记制作
              </button>
            {/if}
          </td>
        </tr>
      {/each}
      {#if order.items.length === 0}
        <tr><td colspan="7" class="muted">从上方菜单点击饮品加入订单</td></tr>
      {/if}
    </tbody>
  </table>

  <div class="cards">
    <ContainerCard {order} {operator} {open} {run} {fmt} />
    <CouponCard {order} {coupons} {open} {run} {fmt} />
  </div>

  <!-- 价格明细：付款前可见 -->
  <div class="pricing">
    <div><span>小计</span><span>{fmt(p.subtotalCents)}</span></div>
    <div class="discount"><span>自带杯折扣</span><span>-{fmt(p.cupDiscountCents)}</span></div>
    <div class="discount"><span>优惠券抵扣</span><span>-{fmt(p.couponDiscountCents)}</span></div>
    <div class="total"><span>应收</span><span>{fmt(p.totalCents)}</span></div>
  </div>

  <!-- 支付 / 退款（本地模拟） -->
  <div class="payment">
    {#if open}
      <select bind:value={payMethod}>
        <option value="MOCK_CASH">现金（模拟）</option>
        <option value="MOCK_QR">扫码（模拟）</option>
      </select>
      <button class="primary" disabled={order.items.length === 0}
        on:click={() => run(() => api.pay(order.id, order.version, payMethod), `已收款 ${fmt(p.totalCents)}`)}>
        收款 {fmt(p.totalCents)}
      </button>
    {/if}
    {#if paid}
      <span class="muted">支付单 #{order.payment.id} · {order.payment.method} · {fmt(order.payment.amountCents)}</span>
      {#if !refunding}
        <button class="danger" on:click={() => (refunding = true)}>退款</button>
      {:else}
        <input placeholder="退款原因" bind:value={refundReason} />
        <button class="danger"
          on:click={() => { refunding = false; run(() => api.refund(order.id, refundReason || '顾客申请退款'), '已退款，优惠已冲回') }}>
          确认退款
        </button>
        <button on:click={() => (refunding = false)}>取消</button>
      {/if}
    {/if}
    {#if refunded && order.refunds.length > 0}
      <div class="refund-info">
        <div>退款单 #{order.refunds[0].id} · 退回 {fmt(order.refunds[0].amountCents)} · {order.refunds[0].reason}</div>
        <div class="muted">
          已冲回自带杯优惠 {fmt(order.refunds[0].reversedCupDiscountCents)}、
          优惠券抵扣 {fmt(order.refunds[0].reversedCouponDiscountCents)}
          {#if order.refunds[0].couponRestored}；优惠券已恢复可再次使用{/if}
        </div>
      </div>
    {/if}
  </div>
</div>
