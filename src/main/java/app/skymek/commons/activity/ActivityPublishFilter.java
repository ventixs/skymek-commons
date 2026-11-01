package app.skymek.commons.activity;

import app.skymek.commons.security.Roles;
import app.skymek.commons.security.TokenClaims;
import app.skymek.commons.support.SupportIdentities;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.XAddArgs;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.time.Instant;

/**
 * Publishes every successful authenticated write to the activity stream; never blocks or fails the request.
 * The service names itself with {@value #SERVICE_KEY} (for example {@code ms-data}).
 */
public class ActivityPublishFilter {

    public static final String SERVICE_KEY = "skymek.activity.service";

    private static final Logger LOG = Logger.getLogger(ActivityPublishFilter.class);
    private static final long STREAM_MAX_LENGTH = 100_000;

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @Inject
    ReactiveRedisDataSource redis;

    @ConfigProperty(name = SERVICE_KEY)
    String service;

    @ServerResponseFilter
    public void publishWrite(ContainerRequestContext request, ContainerResponseContext response, ResourceInfo resource) {
        String route = ActivityFields.routeTemplate(resource);
        if (identity.isAnonymous() || !ActivityFields.isRecordable(request.getMethod(), response.getStatus(), route)) {
            return;
        }
        MultivaluedMap<String, String> path = request.getUriInfo().getPathParameters();
        MultivaluedMap<String, String> query = request.getUriInfo().getQueryParameters();
        Object supportSession = identity.getAttribute(SupportIdentities.SESSION_ATTRIBUTE);
        // A system admin's own account may carry a workshop attribute; it says nothing about what they changed.
        boolean useTokenTenant = !identity.hasRole(Roles.SYSTEM_ADMIN);
        Long tallerId = ActivityFields.firstId(path.getFirst("tallerId"), path.getFirst("workshopId"),
                query.getFirst("tallerId"), useTokenTenant ? claim(TokenClaims.WORKSHOP_ID) : null,
                useTokenTenant ? claim(TokenClaims.TALLER_ID) : null);
        Long ownerId = ActivityFields.firstId(path.getFirst("ownerId"), useTokenTenant ? claim(TokenClaims.OWNER_ID) : null);
        String role = supportSession != null ? Roles.SYSTEM_ADMIN : ActivityFields.primaryRole(identity.getRoles());
        var entry = ActivityFields.entry(service, Instant.now(), request.getMethod(), route, response.getStatus(),
                tallerId, ownerId, jwt.getSubject(), claim(TokenClaims.EMAIL), role,
                supportSession == null ? null : supportSession.toString());
        redis.stream(String.class)
                .xadd(ActivityFields.STREAM_KEY, new XAddArgs().maxlen(STREAM_MAX_LENGTH).nearlyExactTrimming(), entry)
                .subscribe().with(id -> { }, error -> LOG.warnf(error, "Activity not published for %s %s", request.getMethod(), route));
    }

    private String claim(String name) {
        return TokenClaims.text(jwt, name);
    }
}
