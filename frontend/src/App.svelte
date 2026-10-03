<script>
  import { onMount } from 'svelte'
  import { api } from './api.js'
  import MenuPanel from './MenuPanel.svelte'
  import OrderList from './OrderList.svelte'
  import OrderPanel from './OrderPanel.svelte'

  let rules = null
  let menu = []
  let coupons = []
  let orders = []
  let order = null // 当前选中订单详情
  let operator = '收银员A'
  let toast = null
  let toastTimer

  onMount(async () => {
    ;[rules, menu, coupons] = await Promise.all([api.rules(), api.menu(), api.coupons()])
    await refreshOrders()
  })

  function showToast(message, kind = 'info') {
    toast = { message, kind }
    clearTimeout(toastTimer)
    toastTimer = setTimeout(() => (toast = null), 4000)
  }

  async function refreshOrders(keepSelection = true) {
    orders = await api.orders()
    coupons = await api.coupons()
    if (keepSelection && order) {
      order = await api.order(order.id)
    }
  }

  async function selectOrder(summary) {
    order = await api.order(summary.id)
  }

  async function newOrder() {
    order = await api.createOrder()
    await refreshOrders(false)
    showToast(`已开单 #${order.id}`)
  }

  /**
   * 所有改价操作的统一入口：
   * 成功 → 用响应里的最新订单刷新界面（价格已重算）；
   * 409 → 订单被他人改过，自动刷新并提示重试。
   */
  async function run(fn, okMessage) {
    try {
      const updated = await fn()
      if (updated && updated.id) order = updated
      if (okMessage) showToast(okMessage, 'ok')
      await refreshOrders()
    } catch (e) {
      if (e.status === 409) {
        if (order) order = await api.order(order.id)
        await refreshOrders()
        showToast('订单刚被其他人修改，已为你刷新，请重试', 'warn')
      } else {
        showToast(e.message, 'error')
      }
    }
  }

  const fmt = (c) => '¥' + (c / 100).toFixed(2)
</script>

<header>
  <h1>🥤 {rules ? rules.shopName : '自带杯核验'} · 收银台</h1>
  <div class="header-right">
    <label>操作员 <input bind:value={operator} /></label>
    <button class="primary" on:click={newOrder}>＋ 新订单</button>
  </div>
</header>

<main>
  <aside>
    <OrderList {orders} selected={order} on:select={(e) => selectOrder(e.detail)} />
  </aside>

  <section class="workspace">
    {#if order}
      <MenuPanel {menu} disabled={order.status !== 'OPEN'}
        on:add={(e) => run(() => api.addItem(order.id, order.version, e.detail), null)} />
      <OrderPanel {order} {rules} {coupons} {operator} {fmt} {run} />
    {:else}
      <div class="empty">
        <p>点击「＋ 新订单」开始点单</p>
        <p class="hint">自带杯核验、优惠券叠加、并发改杯、支付与退款都在订单页内完成</p>
      </div>
    {/if}
  </section>
</main>

{#if toast}
  <div class="toast toast-{toast.kind}">{toast.message}</div>
{/if}
