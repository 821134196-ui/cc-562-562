package com.food.cup.repo;

import com.food.cup.domain.PosOrder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class OrderRepository {

    @PersistenceContext
    EntityManager em;

    public void persist(PosOrder order) {
        em.persist(order);
    }

    public PosOrder findById(Long id) {
        return em.find(PosOrder.class, id);
    }

    /** 强制刷库：使 @Version 立即递增并反映到返回给客户端的视图，保证下一次乐观锁判断正确。 */
    public void flush() {
        em.flush();
    }
}
