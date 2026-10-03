package com.food.cup.web;

import com.food.cup.service.OrderService;
import com.food.cup.web.dto.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final OrderService orders;

    public OrderResource(OrderService orders) {
        this.orders = orders;
    }

    @POST
    public OrderView create(CreateOrderRequest req) {
        return orders.create(req);
    }

    @GET
    @Path("/{id}")
    public OrderView get(@PathParam("id") Long id) {
        return orders.get(id);
    }

    /** 收银员核验顾客自带杯：通过则享折扣；杯口不洁/容量不足登记拒收并切普通杯。 */
    @POST
    @Path("/{id}/inspect")
    public OrderView inspect(@PathParam("id") Long id, InspectRequest req) {
        return orders.inspect(id, req);
    }

    /** 顾客中途换杯；未制作可换并重算，已制作拒绝。 */
    @POST
    @Path("/{id}/switch-cup")
    public OrderView switchCup(@PathParam("id") Long id, SwitchCupRequest req) {
        return orders.switchCup(id, req);
    }

    @POST
    @Path("/{id}/coupon")
    public OrderView applyCoupon(@PathParam("id") Long id, CouponRequest req) {
        return orders.applyCoupon(id, req);
    }

    @POST
    @Path("/{id}/coupon/remove")
    public OrderView removeCoupon(@PathParam("id") Long id, VersionRequest req) {
        return orders.removeCoupon(id, req);
    }

    /** 标记饮品制作完成（此后容器锁定）。 */
    @POST
    @Path("/{id}/prepare")
    public OrderView prepare(@PathParam("id") Long id, VersionRequest req) {
        return orders.markPrepared(id, req);
    }

    @POST
    @Path("/{id}/pay")
    public OrderView pay(@PathParam("id") Long id, VersionRequest req) {
        return orders.pay(id, req);
    }

    @POST
    @Path("/{id}/refund")
    public OrderView refund(@PathParam("id") Long id, VersionRequest req) {
        return orders.refund(id, req);
    }
}
