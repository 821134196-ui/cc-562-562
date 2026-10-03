<script>
  import { createEventDispatcher } from 'svelte'
  export let menu = []
  export let disabled = false
  const dispatch = createEventDispatcher()
  const fmt = (c) => '¥' + (c / 100).toFixed(2)
</script>

<div class="menu">
  {#each menu as drink (drink.id)}
    <div class="drink">
      <div class="drink-name">{drink.name}</div>
      <div class="sizes">
        {#each drink.sizes as s (s.id)}
          <button class="size" {disabled} on:click={() => dispatch('add', s.id)}>
            <span>{s.sizeName} · {s.volumeMl}ml</span>
            <strong>{fmt(s.priceCents)}</strong>
          </button>
        {/each}
      </div>
    </div>
  {/each}
</div>
