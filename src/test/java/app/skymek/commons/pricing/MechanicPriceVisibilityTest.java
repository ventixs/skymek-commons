package app.skymek.commons.pricing;

import app.skymek.commons.testsupport.Identities;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MechanicPriceVisibilityTest {

    private static final Long TALLER_ID = 7L;

    @Mock
    MechanicPricePermissionLookup permissions;

    @Mock
    JsonWebToken jwt;

    @InjectMocks
    MechanicPriceVisibility visibility;

    @BeforeEach
    void noCacheByDefault() {
        visibility.cacheTtl = Duration.ZERO;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"TALLER_ADMIN", "SYSTEM_ADMIN", "OWNER", "VEHICLE_OWNER"})
    void adminsAndOwnersAlwaysSeePricesWithoutLookingUpNorReadingTheTallerClaim(String role) {
        assertTrue(canViewPrices(role));
        verifyNoInteractions(permissions, jwt);
    }

    @Test
    void anonymousCallerIsNotRestricted() {
        assertFalse(MechanicPriceVisibility.isRestricted(null));
    }

    @Test
    void mechanicWithoutTallerClaimDoesNotSeePrices() {
        assertFalse(canViewPrices("MECHANIC"));
        verifyNoInteractions(permissions);
    }

    @Nested
    class MechanicOfATaller {

        @BeforeEach
        void tokenCarriesTheTaller() {
            when(jwt.getClaim("workshopId")).thenReturn(TALLER_ID);
        }

        @ParameterizedTest(name = "taller lets mechanics see prices: {0}")
        @ValueSource(booleans = {false, true})
        void seesPricesOnlyWhenTheTallerAllowsIt(boolean allowed) {
            tallerAllowsPrices(allowed);
            assertEquals(allowed, canViewPrices("MECHANIC"));
        }

        @Test
        void doesNotSeePricesWhenThePermissionCannotBeRead() {
            when(permissions.mechanicCanViewPrices(TALLER_ID))
                    .thenReturn(Uni.createFrom().failure(new IllegalStateException("down")));
            assertFalse(canViewPrices("MECHANIC"));
        }

        @Test
        void doesNotSeePricesWhenThePermissionIsUnknown() {
            when(permissions.mechanicCanViewPrices(TALLER_ID)).thenReturn(Uni.createFrom().nullItem());
            assertFalse(canViewPrices("MECHANIC"));
        }

        @Test
        void withoutACacheEveryRequestReadsThePermission() {
            tallerAllowsPrices(true);
            canViewPrices("MECHANIC");
            canViewPrices("MECHANIC");
            verify(permissions, times(2)).mechanicCanViewPrices(TALLER_ID);
        }

        @Test
        void withACacheThePermissionIsReusedForTheSameTaller() {
            visibility.cacheTtl = Duration.ofSeconds(60);
            tallerAllowsPrices(true);
            assertTrue(canViewPrices("MECHANIC"));
            assertTrue(canViewPrices("MECHANIC"));
            verify(permissions, times(1)).mechanicCanViewPrices(TALLER_ID);
        }

        private void tallerAllowsPrices(boolean allowed) {
            when(permissions.mechanicCanViewPrices(TALLER_ID)).thenReturn(Uni.createFrom().item(allowed));
        }
    }

    private boolean canViewPrices(String role) {
        return visibility.canViewPrices(Identities.withRole(role), jwt).await().indefinitely();
    }
}
