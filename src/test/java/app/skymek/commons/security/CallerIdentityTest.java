package app.skymek.commons.security;

import app.skymek.commons.support.SupportIdentities;
import app.skymek.commons.testsupport.Identities;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallerIdentityTest {

    @Mock
    JsonWebToken jwt;

    private CallerIdentity callerIdentity(SecurityIdentity security) {
        CallerIdentity identity = new CallerIdentity();
        identity.identity = security;
        identity.jwt = jwt;
        return identity;
    }

    @Test
    void anonymousCallerIsNobody(@Mock SecurityIdentity anonymous) {
        when(anonymous.isAnonymous()).thenReturn(true);
        CallerIdentity caller = callerIdentity(anonymous);

        assertAll(
                () -> assertEquals(Caller.nobody(), caller.current()),
                () -> assertNull(caller.subject()),
                () -> assertNull(caller.role()),
                () -> assertNull(caller.tallerId()),
                () -> assertFalse(caller.systemAdmin()));
        verifyNoInteractions(jwt);
    }

    @Nested
    class Current {

        @Test
        void tallerAdminIsStaffOfTheWorkshopClaim() {
            when(jwt.getClaim("workshopId")).thenReturn("7");
            assertEquals(Caller.staffOf(7L), callerIdentity(Identities.withRole("TALLER_ADMIN")).current());
        }

        @Test
        void mechanicIsStaffToo() {
            when(jwt.getClaim("workshopId")).thenReturn(7);
            assertEquals(Caller.staffOf(7L), callerIdentity(Identities.withRole("MECHANIC")).current());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"VEHICLE_OWNER", "OWNER"})
        void ownerClaimCountsWithAnOwnerRole(String role) {
            when(jwt.getClaim("ownerId")).thenReturn("40");
            assertEquals(Caller.owner(40L), callerIdentity(Identities.withRole(role)).current());
        }

        @Test
        void claimsWithoutTheirRoleGiveNoTenant() {
            lenient().when(jwt.getClaim("workshopId")).thenReturn("7");
            lenient().when(jwt.getClaim("ownerId")).thenReturn("40");
            assertEquals(Caller.nobody(), callerIdentity(Identities.withRoles()).current());
        }

        @Test
        void systemAdminIsAdministratorWithoutATenant() {
            assertEquals(Caller.systemAdministrator(), callerIdentity(Identities.withRole("SYSTEM_ADMIN")).current());
        }
    }

    @Nested
    class Claims {

        @Test
        void tallerClaimIsReadWhateverTheRole() {
            when(jwt.getClaim("workshopId")).thenReturn(17L);
            assertEquals(17L, callerIdentity(Identities.withRole("VEHICLE_OWNER")).tallerId());
        }

        @Test
        void requireTallerLetsOnlyItsOwnTallerThrough() {
            when(jwt.getClaim("workshopId")).thenReturn(17L);
            CallerIdentity caller = callerIdentity(Identities.withRole("TALLER_ADMIN"));

            assertDoesNotThrow(() -> caller.requireTaller(17L));
            assertThrows(ForbiddenException.class, () -> caller.requireTaller(18L));
        }

        @Test
        void systemAdminMayActOnAnyTaller() {
            assertDoesNotThrow(() -> callerIdentity(Identities.withRole("SYSTEM_ADMIN")).requireTaller(18L));
        }

        @Test
        void subjectComesFromTheToken() {
            when(jwt.getSubject()).thenReturn("3f2c8a9e-1111-4c5e-9a77-0123456789ab");
            assertEquals("3f2c8a9e-1111-4c5e-9a77-0123456789ab",
                    callerIdentity(Identities.withRole("TALLER_ADMIN")).subject());
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'SYSTEM_ADMIN,OWNER', SYSTEM_ADMIN",
            "'MECHANIC,TALLER_ADMIN', TALLER_ADMIN",
            "VEHICLE_OWNER, OWNER"
    })
    void roleIsTheHighestOneHeld(String roles, String expected) {
        assertEquals(expected, callerIdentity(Identities.withRoles(roles.split(","))).role());
    }

    @Nested
    class SupportSession {

        @Test
        void configureSessionIsSupportWrite() {
            CallerIdentity caller = callerIdentity(
                    Identities.inSupportSession("s-1", "TALLER_ADMIN", SupportIdentities.WRITE_ROLE));

            assertEquals("s-1", caller.supportSessionId());
            assertTrue(caller.supportWrite());
        }

        @Test
        void readSessionIsNotSupportWrite() {
            assertFalse(callerIdentity(Identities.inSupportSession("s-1", "TALLER_ADMIN",
                    SupportIdentities.READ_ONLY_ROLE)).supportWrite());
        }

        @Test
        void writeRoleOutsideASessionIsNotSupportWrite() {
            assertFalse(callerIdentity(Identities.withRole(SupportIdentities.WRITE_ROLE)).supportWrite());
        }
    }
}
