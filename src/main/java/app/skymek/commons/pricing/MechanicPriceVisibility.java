package app.skymek.commons.pricing;

import app.skymek.commons.security.Roles;
import app.skymek.commons.security.TokenClaims;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether the caller may see prices and costs: admins and owners always, a mechanic only when their
 * taller's {@code mechanicCanViewPrices} permission allows it. A service that reads the permission over
 * the network sets {@value #CACHE_TTL_KEY} so every request does not cost a hop.
 */
@ApplicationScoped
public class MechanicPriceVisibility {

    public static final String CACHE_TTL_KEY = "skymek.pricing.permission-cache-ttl";

    @Inject
    MechanicPricePermissionLookup permissions;

    @ConfigProperty(name = CACHE_TTL_KEY, defaultValue = "PT0S")
    Duration cacheTtl;

    private final Map<Long, CachedPermission> cache = new ConcurrentHashMap<>();

    /** Resolves the permission for the current caller; fails closed when the taller is unknown or unreadable. */
    public Uni<Boolean> canViewPrices(SecurityIdentity identity, JsonWebToken jwt) {
        if (!isRestricted(identity)) {
            return Uni.createFrom().item(true);
        }
        Long tallerId = TokenClaims.tallerId(jwt);
        if (tallerId == null) {
            return Uni.createFrom().item(false);
        }
        CachedPermission cached = cache.get(tallerId);
        if (cached != null && Instant.now().isBefore(cached.until())) {
            return Uni.createFrom().item(cached.canViewPrices());
        }
        return permissions.mechanicCanViewPrices(tallerId)
                .map(Boolean.TRUE::equals)
                .invoke(canViewPrices -> remember(tallerId, canViewPrices))
                .onFailure().recoverWithItem(false);
    }

    /** Whether the caller is a mechanic whose price visibility depends on the taller permission. */
    public static boolean isRestricted(SecurityIdentity identity) {
        return identity != null && !identity.isAnonymous() && identity.hasRole(Roles.MECHANIC)
                && !identity.hasRole(Roles.SYSTEM_ADMIN) && !identity.hasRole(Roles.TALLER_ADMIN);
    }

    private void remember(Long tallerId, boolean canViewPrices) {
        if (cacheTtl != null && cacheTtl.isPositive()) {
            cache.put(tallerId, new CachedPermission(canViewPrices, Instant.now().plus(cacheTtl)));
        }
    }

    private record CachedPermission(boolean canViewPrices, Instant until) {
    }
}
