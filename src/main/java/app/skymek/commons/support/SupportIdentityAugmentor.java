package app.skymek.commons.support;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Turns a SYSTEM_ADMIN request carrying {@code X-Support-Session} into the target's identity.
 * Fails closed: any doubt about the session yields an identity with no roles, never the admin's.
 */
@ApplicationScoped
public class SupportIdentityAugmentor implements SecurityIdentityAugmentor {

    private static final Logger LOG = Logger.getLogger(SupportIdentityAugmentor.class);
    static final String SESSION_MANAGEMENT_PATH = "/admin/support-sessions";

    @Inject
    SupportGrantLookup lookup;

    @Override
    public int priority() {
        return -100;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        // OIDC stores the request under RoutingContext's class name; ROUTING_CONTEXT_ATTRIBUTE is a different key.
        RoutingContext request = HttpSecurityUtils.getRoutingContextAttribute(identity);
        if (identity.isAnonymous() || request == null) {
            return Uni.createFrom().item(identity);
        }
        String sessionId = request.request().getHeader(SupportIdentities.HEADER);
        if (sessionId == null || sessionId.isBlank() || request.normalizedPath().contains(SESSION_MANAGEMENT_PATH)) {
            return Uni.createFrom().item(identity);
        }
        if (!identity.hasRole("SYSTEM_ADMIN") || !(identity.getPrincipal() instanceof OidcJwtCallerPrincipal principal)) {
            return Uni.createFrom().item(SupportIdentities.denied(identity));
        }
        String sub = principal.getSubject();
        return lookup.find(sessionId.trim(), sub, request.request().getHeader("Authorization"))
                .map(grant -> grant.filter(g -> g.isUsableBy(sub, Instant.now())))
                .map(grant -> {
                    if (grant.isEmpty()) {
                        return SupportIdentities.denied(identity);
                    }
                    try {
                        return SupportIdentities.replace(identity, principal, grant.get());
                    } catch (Exception e) {
                        LOG.warnf(e, "Support session %s could not be applied", sessionId);
                        return SupportIdentities.denied(identity);
                    }
                })
                .onFailure().recoverWithItem(error -> {
                    LOG.warnf(error, "Support session %s could not be verified", sessionId);
                    return SupportIdentities.denied(identity);
                });
    }
}
