<script>
  import { api } from './api.js'

  export let order
  export let coupons
  export let open
  export let run
  export let fmt

  let code = ''

  $: applied = order.coupon
  $: notStacked = applied && order.pricing.couponDiscountCents === 0

  function couponText(c) {
    return c.type === 'PERCENT' ? `减${c.value}%` : `减${fmt(c.value)}`
  }

  function apply(c) {
    const target = (c || code).trim()
    if (!target) return
    code = ''
    run(() => api.applyCoupon(order.id, order.version, target), null)
  }
</script>

<div class="card">
  <h3>优惠券</h3>

  {#if applied}
    <div class="applied">
      <span class="coupon-tag">{applied.code} · {couponText(applied)}</span>
      {#if notStacked}
        <span class="warn-line">⚠ 与自带杯折扣不可叠加，本单未抵扣</span>
      {/if}
      {#if open}
        <button on:click={() => run(() => api.removeCoupon(order.id, order.version), '已移除优惠券')}>移除</button>
      {/if}
    </div>
  {:else if open}
    <div class="coupon-input">
      <input placeholder="输入券码" bind:value={code} on:keydown={(e) => e.key === 'Enter' && apply()} />
      <button on:click={() => apply()}>使用</button>
    </div>
    <div class="available">
      {#each coupons.filter((c) => c.status === 'ACTIVE') as c (c.id)}
        <button class="coupon-chip" title={c.stackableWithCupDiscount ? '可与自带杯折扣叠加' : '不可与自带杯折扣叠加'}
          on:click={() => apply(c.code)}>
          {c.code} · {couponText(c)}
          {#if c.minSpendCents > 0}<span class="muted">满{fmt(c.minSpendCents)}</span>{/if}
          <span class="muted">{c.stackableWithCupDiscount ? '可叠加' : '不叠加'}</span>
        </button>
      {/each}
    </div>
  {:else}
    <span class="muted">未使用优惠券</span>
  {/if}
</div>
