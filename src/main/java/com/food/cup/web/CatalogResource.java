package com.food.cup.web;

import com.food.cup.domain.Beverage;
import com.food.cup.domain.Coupon;
import com.food.cup.repo.BeverageRepository;
import com.food.cup.repo.CouponRepository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    public record BeverageDto(Long id, String name, String sizeName, int sizeMl, long basePriceCents) {
        static BeverageDto of(Beverage b) {
            return new BeverageDto(b.getId(), b.getName(), b.getSizeName(), b.getSizeMl(), b.getBasePriceCents());
        }
    }

    public record CouponDto(String code, String description, String type, long value,
                            boolean stackableWithPersonal, String status) {
        static CouponDto of(Coupon c) {
            return new CouponDto(c.getCode(), c.getDescription(), c.getType().name(), c.getValue(),
                    c.isStackableWithPersonal(), c.getStatus().name());
        }
    }

    private final BeverageRepository beverages;
    private final CouponRepository coupons;

    public CatalogResource(BeverageRepository beverages, CouponRepository coupons) {
        this.beverages = beverages;
        this.coupons = coupons;
    }

    @GET
    @Path("/beverages")
    public List<BeverageDto> beverages() {
        return beverages.listAll().stream().map(BeverageDto::of).toList();
    }

    @GET
    @Path("/coupons")
    public List<CouponDto> coupons() {
        return coupons.listAll().stream().map(CouponDto::of).toList();
    }
}
