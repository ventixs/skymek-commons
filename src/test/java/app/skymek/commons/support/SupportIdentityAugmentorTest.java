package app.skymek.commons.support;

import app.skymek.commons.testsupport.SupportGrants;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.runtime.OidcJwtCallerPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportIdentityAugmentorTest {

    @Mock
    SupportGrantLookup lookup;

    @Mock
    HttpServerRequest http;

    @Mock
    RoutingContext routing;

    @InjectMocks
    SupportIdentityAugmentor augmentor;

    @Test
    void readSessionReplacesTheAdminWithTheTallerAdminWithoutSystemAdmin() {
        lookupFinds(Optional.of(SupportGrants.taller(53).build()));

        SecurityIdentity result = augment("/api/data/auth/users/me");

        assertFalse(result.hasRole("SYSTEM_ADMIN"));
        assertTrue(result.hasRole("TALLER_ADMIN"));
        assertTrue(result.hasRole(SupportIdentities.READ_ONLY_ROLE));
        assertEquals(SupportGrants.SESSION_ID, result.getAttribute(SupportIdentities.SESSION_ATTRIBUTE));
        Object workshop = ((OidcJwtCallerPrincipal) result.getPrincipal()).getClaim("workshopId");
        assertEquals("53", workshop.toString());
    }

    @Test
    void unknownSessionLeavesNoRolesInsteadOfTheAdmin() {
        lookupFinds(Optional.empty());

        SecurityIdentity result = augment("/api/data/talleres/53");

        assertFalse(result.hasRole("SYSTEM_ADMIN"));
        assertEquals("denied", result.getAttribute(SupportIdentities.SESSION_ATTRIBUTE));
    }

    @Test
    void sessionManagementCallsKeepTheRealAdmin() {
        SecurityIdentity result = augment("/api/data/admin/support-sessions/s-1");

        assertTrue(result.hasRole("SYSTEM_ADMIN"));
        verifyNoInteractions(lookup);
    }

    private void lookupFinds(Optional<SupportGrant> grant) {
        when(http.getHeader("Authorization")).thenReturn("Bearer t");
        when(lookup.find(SupportGrants.SESSION_ID, SupportGrants.ADMIN_SUB, "Bearer t"))
                .thenReturn(Uni.createFrom().item(grant));
    }

    private SecurityIdentity augment(String path) {
        when(http.getHeader(SupportIdentities.HEADER)).thenReturn(SupportGrants.SESSION_ID);
        when(routing.request()).thenReturn(http);
        when(routing.normalizedPath()).thenReturn(path);
        SecurityIdentity adminRequest = QuarkusSecurityIdentity.builder()
                .setPrincipal(new OidcJwtCallerPrincipal(SupportGrants.adminClaims(), new AccessTokenCredential("t")))
                .addRoles(Set.of("SYSTEM_ADMIN", "TALLER_ADMIN"))
                // Same key Quarkus OIDC uses when it builds the identity of a bearer request.
                .addAttribute(RoutingContext.class.getName(), routing)
                .build();
        return augmentor.augment(adminRequest, null).await().indefinitely();
    }
}
