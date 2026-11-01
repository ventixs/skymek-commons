package app.skymek.commons.support;

import app.skymek.commons.testsupport.SupportGrants;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SupportIdentitiesTest {

    @Nested
    class DeriveClaims {

        @Test
        void tallerSessionBecomesThatTallersAdminWithoutSystemAdmin() throws Exception {
            JwtClaims derived = SupportIdentities.deriveClaims(SupportGrants.adminClaims(),
                    SupportGrants.taller(42).write().build());

            assertEquals(42L, ((Number) derived.getClaimValue("workshopId")).longValue());
            assertEquals(SupportGrants.ADMIN_SUB, derived.getSubject());
            Map<?, ?> access = (Map<?, ?>) derived.getClaimValue("realm_access");
            assertEquals(Set.of("TALLER_ADMIN", SupportIdentities.WRITE_ROLE), Set.copyOf((List<?>) access.get("roles")));
            assertNull(derived.getClaimValue("ownerId"));
        }

        @Test
        void ownerSessionDropsTheAdminsOwnWorkshop() throws Exception {
            JwtClaims derived = SupportIdentities.deriveClaims(SupportGrants.adminClaims(),
                    SupportGrants.owner(9).build());

            assertNull(derived.getClaimValue("workshopId"));
            assertEquals(9L, ((Number) derived.getClaimValue("ownerId")).longValue());
        }
    }

    @Test
    void readOwnerSessionIsAReadOnlyVehicleOwner() {
        assertEquals(Set.of("VEHICLE_OWNER", "support_readonly"),
                SupportIdentities.rolesFor(SupportGrants.owner(9).build()));
    }
}
