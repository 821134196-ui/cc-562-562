package com.food.cup.repo;

import com.food.cup.domain.Coupon;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CouponRepository {

    @PersistenceContext
    EntityManager em;

    public Optional<Coupon> findByCode(String code) {
        return em.createQuery("select c from Coupon c where c.code = :code", Coupon.class)
                .setParameter("code", code)
                .getResultList().stream().findFirst();
    }

    public List<Coupon> listAll() {
        return em.createQuery("select c from Coupon c order by c.id", Coupon.class).getResultList();
    }
}
