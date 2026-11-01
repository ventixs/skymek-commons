package app.skymek.commons.support;

import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Finds an active support session. Every service that depends on this library provides exactly one bean:
 * the service that owns the sessions reads its table, the others ask that service over HTTP.
 */
public interface SupportGrantLookup {

    /**
     * @param authorizationHeader the admin's own {@code Authorization} header, for a remote lookup
     * @return the session, or empty when it does not exist or is no longer active
     */
    Uni<Optional<SupportGrant>> find(String sessionId, String adminSub, String authorizationHeader);
}
