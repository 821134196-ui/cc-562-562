const BASE = '/api'

/** 统一请求封装；409 时抛出带标记的错误，由调用方刷新订单 */
async function req(path, { method = 'GET', body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined
  })
  if (!res.ok) {
    let payload = {}
    try { payload = await res.json() } catch { /* ignore */ }
    const err = new Error(payload.message || `请求失败（${res.status}）`)
    err.status = res.status
    err.code = payload.code
    throw err
  }
  return res.json()
}

export const api = {
  rules: () => req('/rules'),
  menu: () => req('/menu'),
  coupons: () => req('/coupons'),
  orders: () => req('/orders'),
  order: (id) => req(`/orders/${id}`),
  createOrder: () => req('/orders', { method: 'POST', body: { items: [] } }),
  addItem: (id, v, drinkSizeId, quantity = 1) =>
    req(`/orders/${id}/items`, { method: 'POST', body: { expectedVersion: v, drinkSizeId, quantity } }),
  verifyCup: (id, v, capacityMl, hygiene, operator) =>
    req(`/orders/${id}/container/own-cup/verify`, {
      method: 'POST',
      body: { expectedVersion: v, capacityMl, hygiene, inspector: operator, changedBy: operator }
    }),
  storeCup: (id, v, operator) =>
    req(`/orders/${id}/container/store-cup`, { method: 'POST', body: { expectedVersion: v, changedBy: operator } }),
  applyCoupon: (id, v, code) =>
    req(`/orders/${id}/coupon`, { method: 'POST', body: { expectedVersion: v, code } }),
  removeCoupon: (id, v) =>
    req(`/orders/${id}/coupon/remove`, { method: 'POST', body: { expectedVersion: v } }),
  markMade: (id, itemId, v) =>
    req(`/orders/${id}/items/${itemId}/made`, { method: 'POST', body: { expectedVersion: v } }),
  pay: (id, v, method) =>
    req(`/orders/${id}/pay`, { method: 'POST', body: { expectedVersion: v, method } }),
  refund: (id, reason) =>
    req(`/orders/${id}/refund`, { method: 'POST', body: { reason } })
}
