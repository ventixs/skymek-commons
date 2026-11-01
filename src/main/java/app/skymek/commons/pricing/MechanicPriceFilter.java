package app.skymek.commons.pricing;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.util.Map;

/**
 * Enforces the "mechanic without prices" rule for every endpoint: endpoints marked
 * {@link RequiresPriceVisibility} answer 403 and any other response carrying money is redacted through the
 * service's {@link PriceRedactor}. Only mechanics are affected; admins and owners pass through.
 */
public class MechanicPriceFilter {

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @Inject
    MechanicPriceVisibility priceVisibility;

    @Inject
    PriceRedactor redactor;

    @ServerRequestFilter
    public Uni<Response> rejectFinancialEndpoints(ContainerRequestContext request, ResourceInfo resource) {
        if (!requiresPriceVisibility(resource) || !MechanicPriceVisibility.isRestricted(identity)) {
            return Uni.createFrom().nullItem();
        }
        return priceVisibility.canViewPrices(identity, jwt)
                .map(canViewPrices -> canViewPrices ? null : forbidden());
    }

    @ServerResponseFilter
    public Uni<Void> redactPrices(ContainerResponseContext response) {
        Object entity = response.getEntity();
        if (!MechanicPriceVisibility.isRestricted(identity) || !redactor.carriesPrices(entity)) {
            return Uni.createFrom().voidItem();
        }
        return priceVisibility.canViewPrices(identity, jwt)
                .invoke(canViewPrices -> {
                    if (!canViewPrices) {
                        response.setEntity(redactor.redact(entity));
                    }
                })
                .replaceWithVoid();
    }

    private static boolean requiresPriceVisibility(ResourceInfo resource) {
        if (resource == null || resource.getResourceMethod() == null) {
            return false;
        }
        return resource.getResourceMethod().isAnnotationPresent(RequiresPriceVisibility.class)
                || resource.getResourceClass().isAnnotationPresent(RequiresPriceVisibility.class);
    }

    private static Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "FORBIDDEN", "message", "Prices are not visible to this mechanic"))
                .build();
    }
}
