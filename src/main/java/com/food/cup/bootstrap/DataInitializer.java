package com.food.cup.bootstrap;

import com.food.cup.domain.*;
import com.food.cup.repo.BeverageRepository;
import com.food.cup.repo.CouponRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.hibernate.Session;

/** 启动时：调优 SQLite 连接、写入演示用饮品规格与优惠券（幂等）。 */
@ApplicationScoped
public class DataInitializer {

    @PersistenceContext
    EntityManager em;

    private final BeverageRepository beverages;
    private final CouponRepository coupons;

    public DataInitializer(BeverageRepository beverages, CouponRepository coupons) {
        this.beverages = beverages;
        this.coupons = coupons;
    }

    public void onStart(@Observes StartupEvent ev) {
        // PRAGMA 必须在事务外执行；WAL 模式下读不阻塞写，支撑两人同时改杯
        em.unwrap(Session.class).doWork(connection -> {
            try (var st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA foreign_keys=ON");
            }
        });
        seed();
    }

    @Transactional
    void seed() {
        if (beverages.count() == 0) {
            em.persist(new Beverage("美式咖啡", "中杯", 360, 1800));
            em.persist(new Beverage("美式咖啡", "大杯", 500, 2200));
            em.persist(new Beverage("燕麦拿铁", "中杯", 400, 2400));
            em.persist(new Beverage("燕麦拿铁", "大杯", 550, 2800));
            em.persist(new Beverage("满杯鲜果茶", "大杯", 600, 2600));
        }
        if (coupons.listAll().isEmpty()) {
            // 规则：百分比券不可与自带杯折扣叠加；立减券允许叠加（但折扣按顺序作用于折后价，不会重复抵扣）
            em.persist(new Coupon("NEW10", "新客 9 折（不可与自带杯叠加）", CouponType.PERCENT, 10, false));
            em.persist(new Coupon("VIP15", "会员 85 折（不可与自带杯叠加）", CouponType.PERCENT, 15, false));
            em.persist(new Coupon("SAVE200", "立减 2 元（可与自带杯叠加）", CouponType.FIXED, 200, true));
        }
    }
}
