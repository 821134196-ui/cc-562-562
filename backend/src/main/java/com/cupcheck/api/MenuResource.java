package com.cupcheck.api;

import java.util.List;

import com.cupcheck.model.Drink;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.Blocking;

@Blocking
@Path("/api/menu")
@Produces(MediaType.APPLICATION_JSON)
public class MenuResource {

    @jakarta.inject.Inject
    EntityManager em;

    public record SizeDto(long id, String sizeName, int volumeMl, int priceCents) {
    }

    public record DrinkDto(long id, String name, String description, List<SizeDto> sizes) {
    }

    @GET
    public List<DrinkDto> menu() {
        return em.createQuery("select d from Drink d join fetch d.sizes order by d.id", Drink.class)
                .getResultList().stream()
                .map(d -> new DrinkDto(d.id, d.name, d.description,
                        d.sizes.stream()
                                .map(s -> new SizeDto(s.id, s.sizeName, s.volumeMl, s.priceCents))
                                .toList()))
                .toList();
    }
}
