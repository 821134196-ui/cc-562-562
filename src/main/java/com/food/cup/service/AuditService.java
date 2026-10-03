package com.food.cup.service;

import com.food.cup.domain.EventAction;
import com.food.cup.domain.PosOrder;
import com.food.cup.repo.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * 独立事务留痕：外层业务事务即将回滚（例如已制作订单换杯被拒）时，
 * 审计事件仍要落库，使用 REQUIRES_NEW 提交。
 */
@ApplicationScoped
public class AuditService {

    private final OrderRepository orders;

    public AuditService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordEvent(Long orderId, EventAction action, String detail) {
        PosOrder order = orders.findById(orderId);
        if (order != null) {
            order.addEvent(action, detail);
        }
    }
}
