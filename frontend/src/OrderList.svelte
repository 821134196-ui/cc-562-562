<script>
  import { createEventDispatcher } from 'svelte'
  export let orders = []
  export let selected = null
  const dispatch = createEventDispatcher()

  const statusLabel = { OPEN: '点单中', PAID: '已支付', REFUNDED: '已退款' }
  const containerLabel = { OWN_CUP: '自带杯', STORE_CUP: '普通杯', NONE: '—' }
  const fmt = (c) => '¥' + (c / 100).toFixed(2)
</script>

<div class="order-list">
  <h2>订单</h2>
  {#if orders.length === 0}
    <p class="muted">暂无订单</p>
  {/if}
  {#each orders as o (o.id)}
    <button
      class="order-row"
      class:selected={selected && selected.id === o.id}
      on:click={() => dispatch('select', o)}
    >
      <span class="oid">#{o.id}</span>
      <span class="badge badge-{o.status.toLowerCase()}">{statusLabel[o.status]}</span>
      <span class="muted">{containerLabel[o.containerType] || '—'}</span>
      <span class="amount">{fmt(o.totalCents)}</span>
    </button>
  {/each}
</div>
