package com.food.cup.repo;

import com.food.cup.domain.Beverage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class BeverageRepository {

    @PersistenceContext
    EntityManager em;

    public Optional<Beverage> find(Long id) {
        return Optional.ofNullable(em.find(Beverage.class, id));
    }

    public List<Beverage> listAll() {
        return em.createQuery("select b from Beverage b order by b.id", Beverage.class).getResultList();
    }

    public long count() {
        return em.createQuery("select count(b) from Beverage b", Long.class).getSingleResult();
    }
}
