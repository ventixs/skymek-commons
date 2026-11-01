package app.skymek.commons.security;

import app.skymek.commons.support.SupportIdentities;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * The effective caller of the current request. During a support session the augmentor has already replaced
 * the token claims and roles with the target's, so the admin is seen as the taller or owner they act as,
 * never as SYSTEM_ADMIN.
 */
@RequestScoped
public class CallerIdentity {

    private static final List<String> ROLE_PRIORITY =
            List.of(Roles.SYSTEM_ADMIN, Roles.TALLER_ADMIN, Roles.MECHANIC, Roles.OWNER, Roles.VEHICLE_OWNER);

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    /**
     * The caller with the tenant its role entitles it to: a taller only for TALLER_ADMIN or MECHANIC and an
     * owner only for an owner role, so a stray claim on another account grants nothing.
     */
    public Caller current() {
        if (anonymous()) {
            return Caller.nobody();
        }
        boolean staff = identity.hasRole(Roles.TALLER_ADMIN) || identity.hasRole(Roles.MECHANIC);
        boolean owner = identity.hasRole(Roles.VEHICLE_OWNER) || identity.hasRole(Roles.OWNER);
        return new Caller(identity.hasRole(Roles.SYSTEM_ADMIN),
                staff ? TokenClaims.tallerId(jwt) : null,
                owner ? TokenClaims.ownerId(jwt) : null);
    }

    public boolean anonymous() {
        return identity == null || identity.isAnonymous();
    }

    public boolean systemAdmin() {
        return !anonymous() && identity.hasRole(Roles.SYSTEM_ADMIN);
    }

    /** The highest role the caller holds, with VEHICLE_OWNER reported as OWNER; null when it has none. */
    public String role() {
        if (anonymous()) {
            return null;
        }
        return ROLE_PRIORITY.stream()
                .filter(identity::hasRole)
                .map(role -> Roles.VEHICLE_OWNER.equals(role) ? Roles.OWNER : role)
                .findFirst()
                .orElse(null);
    }

    /** The taller claim whatever the role; prefer {@link #current()} for authorization decisions. */
    public Long tallerId() {
        return anonymous() ? null : TokenClaims.tallerId(jwt);
    }

    /** The owner claim whatever the role; prefer {@link #current()} for authorization decisions. */
    public Long ownerId() {
        return anonymous() ? null : TokenClaims.ownerId(jwt);
    }

    /** The Keycloak subject, or null for an anonymous caller or a token without one. */
    public String subject() {
        return anonymous() ? null : TokenClaims.subject(jwt);
    }

    public String email() {
        return anonymous() ? null : TokenClaims.text(jwt, TokenClaims.EMAIL);
    }

    /** The support session the request runs in, or null outside one. */
    public String supportSessionId() {
        if (anonymous()) {
            return null;
        }
        Object session = identity.getAttribute(SupportIdentities.SESSION_ATTRIBUTE);
        return session == null ? null : session.toString();
    }

    /** A system admin acting as the client in a «Configurar» support session. */
    public boolean supportWrite() {
        return supportSessionId() != null && identity.hasRole(SupportIdentities.WRITE_ROLE);
    }

    /** Throws 403 unless the caller is a system admin or its taller claim is {@code tallerId}. */
    public void requireTaller(Long tallerId) {
        if (!systemAdmin() && (tallerId == null || !tallerId.equals(tallerId()))) {
            throw new ForbiddenException("No access to this taller");
        }
    }
}
