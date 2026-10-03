package com.cupcheck.api;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.Blocking;

/** 计价规则与门店信息，前端启动时拉取 */
@Blocking
@Path("/api/rules")
@Produces(MediaType.APPLICATION_JSON)
public class RuleResource {

    @ConfigProperty(name = "app.cup-discount-cents")
    int cupDiscountCents;

    @ConfigProperty(name = "app.shop-name")
    String shopName;

    public record RulesDto(String shopName, int cupDiscountPerItemCents, String currency,
            String[] hygieneOptions, String[] paymentMethods) {
    }

    @GET
    public RulesDto rules() {
        return new RulesDto(shopName, cupDiscountCents, "CNY",
                new String[] { "CLEAN", "DIRTY_RIM", "RESIDUE", "DAMAGED" },
                new String[] { "MOCK_CASH", "MOCK_QR" });
    }
}
