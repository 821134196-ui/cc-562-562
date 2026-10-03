package com.cupcheck.api;

import java.util.List;

import com.cupcheck.model.Coupon;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.Blocking;

@Blocking
@Path("/api/coupons")
@Produces(MediaType.APPLICATION_JSON)
public class CouponResource {

    @jakarta.inject.Inject
    EntityManager em;

    public record CouponDto(long id, String code, String type, int value, int minSpendCents,
            boolean stackableWithCupDiscount, String status, Long usedByOrderId) {
        static CouponDto of(Coupon c) {
            return new CouponDto(c.id, c.code, c.type.name(), c.value, c.minSpendCents,
                    c.stackableWithCupDiscount, c.status.name(), c.usedByOrderId);
        }
    }

    @GET
    public List<CouponDto> list() {
        return em.createQuery("select c from Coupon c order by c.id", Coupon.class)
                .getResultList().stream().map(CouponDto::of).toList();
    }
}
