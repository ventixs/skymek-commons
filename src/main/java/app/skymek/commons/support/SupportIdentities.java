package app.skymek.commons.support;

import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces, never widens, the admin's identity during a support session: the admin becomes the taller
 * admin or the owner of the target, without SYSTEM_ADMIN, so no "admin sees everything" branch can leak
 * another tenant's data.
 */
public final class SupportIdentities {

    public static final String HEADER = "X-Support-Session";
    public static final String SESSION_ATTRIBUTE = "supportSessionId";
    public static final String READ_ONLY_ROLE = "support_readonly";
    public static final String WRITE_ROLE = "support_write";
    private static final List<String> TENANT_CLAIMS = List.of("workshopId", "tallerId", "ownerId", "mechanicId", "groups");

    private SupportIdentities() {
    }

    public static Set<String> rolesFor(SupportGrant grant) {
        String role = SupportGrant.TALLER.equals(grant.targetType()) ? "TALLER_ADMIN" : "VEHICLE_OWNER";
        return Set.of(role, grant.readOnly() ? READ_ONLY_ROLE : WRITE_ROLE);
    }

    public static JwtClaims deriveClaims(JwtClaims original, SupportGrant grant) throws InvalidJwtException {
        JwtClaims claims = JwtClaims.parse(original.toJson());
        TENANT_CLAIMS.forEach(claims::unsetClaim);
        if (SupportGrant.TALLER.equals(grant.targetType())) {
            claims.setClaim("workshopId", grant.targetId());
            claims.setClaim("tallerId", grant.targetId());
            claims.setClaim("profileType", "taller_admin");
        } else {
            claims.setClaim("ownerId", grant.targetId());
            claims.setClaim("profileType", "vehicle_owner");
        }
        claims.setClaim("realm_access", Map.of("roles", List.copyOf(rolesFor(grant))));
        claims.setClaim(SESSION_ATTRIBUTE, grant.sessionId());
        return claims;
    }

    public static SecurityIdentity replace(SecurityIdentity admin, OidcJwtCallerPrincipal principal, SupportGrant grant)
            throws InvalidJwtException {
        var builder = QuarkusSecurityIdentity.builder()
                .setPrincipal(new OidcJwtCallerPrincipal(deriveClaims(principal.getClaims(), grant), principal.getCredential()))
                .addCredentials(admin.getCredentials())
                .addRoles(rolesFor(grant))
                .addAttribute(SESSION_ATTRIBUTE, grant.sessionId())
                .setAnonymous(false);
        admin.getAttributes().forEach(builder::addAttribute);
        builder.addAttribute(SESSION_ATTRIBUTE, grant.sessionId());
        return builder.build();
    }

    /**
     * No roles and no tenant, in the identity and in the token claims code reads directly: every protected
     * endpoint answers 401/403 instead of falling back to the admin.
     */
    public static SecurityIdentity denied(SecurityIdentity admin) {
        if (!(admin.getPrincipal() instanceof OidcJwtCallerPrincipal principal)) {
            return QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        }
        try {
            JwtClaims claims = JwtClaims.parse(principal.getClaims().toJson());
            TENANT_CLAIMS.forEach(claims::unsetClaim);
            claims.unsetClaim("profileType");
            claims.setClaim("realm_access", Map.of("roles", List.of()));
            var builder = QuarkusSecurityIdentity.builder()
                    .setPrincipal(new OidcJwtCallerPrincipal(claims, principal.getCredential()))
                    .addCredentials(admin.getCredentials())
                    .setAnonymous(false);
            admin.getAttributes().forEach(builder::addAttribute);
            builder.addAttribute(SESSION_ATTRIBUTE, "denied");
            return builder.build();
        } catch (InvalidJwtException e) {
            return QuarkusSecurityIdentity.builder().setAnonymous(true).build();
        }
    }
}
