<script>
  import { api } from './api.js'

  export let order
  export let operator
  export let open
  export let run
  export let fmt

  let verifying = false
  let capacityMl = 500
  let hygiene = 'CLEAN'

  const hygieneOptions = [
    { value: 'CLEAN', label: '杯口清洁' },
    { value: 'DIRTY_RIM', label: '杯口不洁' },
    { value: 'RESIDUE', label: '杯内有残留' },
    { value: 'DAMAGED', label: '杯体破损' }
  ]
  const hygieneLabel = Object.fromEntries(hygieneOptions.map((o) => [o.value, o.label]))

  function submitVerify() {
    verifying = false
    run(
      () => api.verifyCup(order.id, order.version, Number(capacityMl), hygiene, operator),
      hygiene === 'CLEAN' ? '核验完成，价格已重算' : '已登记拒收并切换普通杯'
    )
  }
</script>

<div class="card">
  <h3>容器</h3>
  <div class="current">
    {#if order.container}
      {#if order.container.type === 'OWN_CUP'}
        <span class="cup own">🥤 自带杯 · {order.container.capacityMl}ml · 已核验</span>
      {:else}
        <span class="cup store">🥛 门店普通杯</span>
      {/if}
      <span class="muted">{order.container.note} · {order.container.changedBy}</span>
    {:else}
      <span class="muted">未选择</span>
    {/if}
  </div>

  {#if open}
    <div class="container-actions">
      {#if !verifying}
        <button class="primary" on:click={() => (verifying = true)}>核验自带杯</button>
        <button on:click={() => run(() => api.storeCup(order.id, order.version, operator), '已切换普通杯')}>
          切换普通杯
        </button>
      {:else}
        <div class="verify-form">
          <label>杯容量(ml) <input type="number" min="1" bind:value={capacityMl} /></label>
          <label>卫生检查
            <select bind:value={hygiene}>
              {#each hygieneOptions as o}<option value={o.value}>{o.label}</option>{/each}
            </select>
          </label>
          {#if order.requiredCapacityMl > 0 && capacityMl < order.requiredCapacityMl}
            <div class="warn-line">⚠ 容量不足 {order.requiredCapacityMl}ml，将被拒收</div>
          {/if}
          {#if hygiene !== 'CLEAN'}
            <div class="warn-line">⚠ 卫生不合格，将登记拒收记录（不可删除）</div>
          {/if}
          <button class="primary" on:click={submitVerify}>提交核验</button>
          <button on:click={() => (verifying = false)}>取消</button>
        </div>
      {/if}
    </div>
  {/if}

  {#if order.rejections.length > 0}
    <div class="rejections">
      <h4>拒收记录（不可删除）</h4>
      {#each order.rejections as r (r.id)}
        <div class="rejection">✖ {r.reason} <span class="muted">{r.operator} · {new Date(r.createdAt).toLocaleTimeString()}</span></div>
      {/each}
    </div>
  {/if}

  {#if order.inspections.length > 0}
    <details>
      <summary>核验记录（{order.inspections.length}）</summary>
      {#each order.inspections as i (i.id)}
        <div class="line">
          {i.accepted ? '✔' : '✖'} {i.capacityMl}ml / 需{i.requiredCapacityMl}ml · {hygieneLabel[i.hygiene]} · {i.inspector}
        </div>
      {/each}
    </details>
  {/if}

  {#if order.containerHistory.length > 1}
    <details>
      <summary>换杯历史（{order.containerHistory.length}）</summary>
      {#each [...order.containerHistory].reverse() as c (c.id)}
        <div class="line" class:active-line={c.active}>
          {c.active ? '→' : '·'}
          {c.type === 'OWN_CUP' ? `自带杯 ${c.capacityMl}ml` : '普通杯'} · {c.note} · {c.changedBy}
        </div>
      {/each}
    </details>
  {/if}
</div>
