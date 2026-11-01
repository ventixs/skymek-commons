package app.skymek.commons.support;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.util.Map;
import java.util.Optional;

/** Enforces READ and the owner-only writes for requests running inside a support session. */
public class SupportModeFilter {

    @Inject
    SecurityIdentity identity;

    @ServerRequestFilter
    public Optional<Response> enforce(ContainerRequestContext request) {
        Object session = identity.getAttribute(SupportIdentities.SESSION_ATTRIBUTE);
        if (session == null) {
            return Optional.empty();
        }
        boolean readOnly = identity.hasRole(SupportIdentities.READ_ONLY_ROLE) || "denied".equals(session);
        if (SupportModeRules.allows(readOnly, request.getMethod(), request.getUriInfo().getPath())) {
            return Optional.empty();
        }
        return Optional.of(Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "SUPPORT_MODE", "message", "Not allowed in support mode"))
                .build());
    }
}
