package com.cupcheck.api;

import java.util.List;

import com.cupcheck.api.dto.OrderDetailDto;
import com.cupcheck.api.dto.OrderMapper;
import com.cupcheck.model.HygieneResult;
import com.cupcheck.model.Order;
import com.cupcheck.service.OrderService;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.common.annotation.Blocking;

/**
 * 收银台订单接口。所有改价操作都带 expectedVersion（乐观锁），
 * 两人同时改杯时后提交者得到 409，前端刷新后重试即可。
 */
@Blocking
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService orders;

    @Inject
    OrderMapper mapper;

    // ---------- 请求体 ----------

    @RegisterForReflection
    public record CreateOrderRequest(List<Item> items) {
        @RegisterForReflection
        public record Item(long drinkSizeId, int quantity) {
        }
    }

    @RegisterForReflection
    public record AddItemRequest(long expectedVersion, long drinkSizeId, int quantity) {
    }

    @RegisterForReflection
    public record StoreCupRequest(long expectedVersion, String changedBy, String note) {
    }

    @RegisterForReflection
    public record VerifyCupRequest(long expectedVersion, int capacityMl, HygieneResult hygiene,
            String inspector, String changedBy) {
    }

    @RegisterForReflection
    public record CouponRequest(long expectedVersion, String code) {
    }

    @RegisterForReflection
    public record VersionRequest(long expectedVersion) {
    }

    @RegisterForReflection
    public record PayRequest(long expectedVersion, String method) {
    }

    @RegisterForReflection
    public record RefundRequest(String reason) {
    }

    // ---------- 查询 ----------

    @GET
    @Transactional(Transactional.TxType.REQUIRED)
    public List<OrderDetailDto.SummaryDto> list() {
        return mapper.toSummaries(orders.listAll());
    }

    @GET
    @Path("{id}")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto get(@PathParam("id") long id) {
        return mapper.toDetail(orders.get(id));
    }

    @GET
    @Path("{id}/rejections")
    @Transactional(Transactional.TxType.REQUIRED)
    public List<OrderDetailDto.RejectionDto> rejections(@PathParam("id") long id) {
        return mapper.toDetail(orders.get(id)).rejections();
    }

    // ---------- 创建与加购 ----------

    @POST
    @Transactional(Transactional.TxType.REQUIRED)
    public Response create(CreateOrderRequest req) {
        List<OrderService.NewItem> items = req == null || req.items() == null
                ? List.of()
                : req.items().stream().map(i -> new OrderService.NewItem(i.drinkSizeId(), i.quantity())).toList();
        Order order = orders.create(items);
        return Response.status(Response.Status.CREATED).entity(mapper.toDetail(order)).build();
    }

    @POST
    @Path("{id}/items")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto addItem(@PathParam("id") long id, AddItemRequest req) {
        return mapper.toDetail(orders.addItem(id, req.expectedVersion(), req.drinkSizeId(), req.quantity()));
    }

    // ---------- 容器 ----------

    @POST
    @Path("{id}/container/store-cup")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto storeCup(@PathParam("id") long id, StoreCupRequest req) {
        return mapper.toDetail(orders.switchToStoreCup(id, req.expectedVersion(), req.changedBy(), req.note()));
    }

    @POST
    @Path("{id}/container/own-cup/verify")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto verifyOwnCup(@PathParam("id") long id, VerifyCupRequest req) {
        return mapper.toDetail(orders.verifyOwnCup(id, req.expectedVersion(), req.capacityMl(),
                req.hygiene(), req.inspector(), req.changedBy()));
    }

    // ---------- 优惠券 ----------

    @POST
    @Path("{id}/coupon")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto applyCoupon(@PathParam("id") long id, CouponRequest req) {
        return mapper.toDetail(orders.applyCoupon(id, req.expectedVersion(), req.code()));
    }

    @POST
    @Path("{id}/coupon/remove")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto removeCoupon(@PathParam("id") long id, VersionRequest req) {
        return mapper.toDetail(orders.removeCoupon(id, req.expectedVersion()));
    }

    // ---------- 制作 / 支付 / 退款 ----------

    @POST
    @Path("{id}/items/{itemId}/made")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto made(@PathParam("id") long id, @PathParam("itemId") long itemId, VersionRequest req) {
        return mapper.toDetail(orders.markItemMade(id, itemId, req.expectedVersion()));
    }

    @POST
    @Path("{id}/pay")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto pay(@PathParam("id") long id, PayRequest req) {
        return mapper.toDetail(orders.pay(id, req.expectedVersion(), req.method()));
    }

    @POST
    @Path("{id}/refund")
    @Transactional(Transactional.TxType.REQUIRED)
    public OrderDetailDto refund(@PathParam("id") long id, RefundRequest req) {
        return mapper.toDetail(orders.refund(id, req == null ? null : req.reason()));
    }
}
