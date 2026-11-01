package app.skymek.commons.testsupport;

import app.skymek.commons.support.SupportGrant;
import org.jose4j.jwt.JwtClaims;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Support sessions opened by {@link #ADMIN_SUB}: by default a READ session on taller 42 that has not expired. */
public final class SupportGrants {

    public static final String ADMIN_SUB = "admin-sub";
    public static final String SESSION_ID = "s-1";

    private String targetType = SupportGrant.TALLER;
    private long targetId = 42L;
    private String mode = SupportGrant.READ;
    private Instant expiresAt = Instant.now().plusSeconds(1800);

    private SupportGrants() {
    }

    public static SupportGrants taller(long tallerId) {
        SupportGrants grant = new SupportGrants();
        grant.targetId = tallerId;
        return grant;
    }

    public static SupportGrants owner(long ownerId) {
        SupportGrants grant = taller(ownerId);
        grant.targetType = SupportGrant.OWNER;
        return grant;
    }

    public SupportGrants write() {
        mode = SupportGrant.WRITE;
        return this;
    }

    public SupportGrants expiringAt(Instant instant) {
        expiresAt = instant;
        return this;
    }

    public SupportGrant build() {
        return new SupportGrant(SESSION_ID, ADMIN_SUB, targetType, targetId, mode, expiresAt);
    }

    /** Token claims of the system admin who opens the sessions; the admin also belongs to workshop 1. */
    public static JwtClaims adminClaims() {
        JwtClaims claims = new JwtClaims();
        claims.setSubject(ADMIN_SUB);
        claims.setClaim("email", "admin@example.com");
        claims.setClaim("workshopId", 1);
        claims.setClaim("realm_access", Map.of("roles", List.of("SYSTEM_ADMIN", "TALLER_ADMIN")));
        return claims;
    }
}
