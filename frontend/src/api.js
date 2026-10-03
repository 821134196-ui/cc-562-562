// 后端 API 封装。所有写操作都带当前订单 version（乐观锁），
// 409 时抛出 versionConflict=true，由界面提示刷新。
const BASE = '/api';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error(body?.message || `请求失败 (${res.status})`);
    err.code = body?.code;
    err.status = res.status;
    err.versionConflict = res.status === 409;
    throw err;
  }
  return body;
}

export const api = {
  listBeverages: () => request('/beverages'),
  listCoupons: () => request('/coupons'),
  getOrder: (id) => request('/orders/' + id),
  createOrder: (payload) =>
    request('/orders', { method: 'POST', body: JSON.stringify(payload) }),
  inspect: (id, payload) =>
    request(`/orders/${id}/inspect`, { method: 'POST', body: JSON.stringify(payload) }),
  switchCup: (id, payload) =>
    request(`/orders/${id}/switch-cup`, { method: 'POST', body: JSON.stringify(payload) }),
  applyCoupon: (id, payload) =>
    request(`/orders/${id}/coupon`, { method: 'POST', body: JSON.stringify(payload) }),
  removeCoupon: (id, version) =>
    request(`/orders/${id}/coupon/remove`, { method: 'POST', body: JSON.stringify({ version }) }),
  prepare: (id, version) =>
    request(`/orders/${id}/prepare`, { method: 'POST', body: JSON.stringify({ version }) }),
  pay: (id, version) =>
    request(`/orders/${id}/pay`, { method: 'POST', body: JSON.stringify({ version }) }),
  refund: (id, version) =>
    request(`/orders/${id}/refund`, { method: 'POST', body: JSON.stringify({ version }) })
};

export const yuan = (cents) => (cents / 100).toFixed(2);
