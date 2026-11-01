package app.skymek.commons.pricing;

import app.skymek.commons.testsupport.Identities;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
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

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MechanicPriceFilterTest {

    private static final PricedOrder ORDER = new PricedOrder("Cambio de aceite", 120.0);

    @Mock
    MechanicPriceVisibility priceVisibility;

    @Mock
    JsonWebToken jwt;

    @Mock
    ContainerResponseContext response;

    @Mock
    ContainerRequestContext request;

    @Mock
    ResourceInfo resource;

    @InjectMocks
    MechanicPriceFilter filter;

    @BeforeEach
    void serviceRedactsItsPricedOrders() {
        filter.redactor = new OrderRedactor();
    }

    @Nested
    class RedactPrices {

        @Test
        void mechanicWithoutPermissionReceivesTheOrderWithoutPrice() {
            callerIs("MECHANIC");
            mechanicMayViewPrices(false);
            when(response.getEntity()).thenReturn(ORDER);

            redact();

            verify(response).setEntity(new PricedOrder("Cambio de aceite", null));
        }

        @Test
        void mechanicWithPermissionReceivesPrices() {
            callerIs("MECHANIC");
            mechanicMayViewPrices(true);
            when(response.getEntity()).thenReturn(ORDER);

            redact();

            verify(response, never()).setEntity(any());
        }

        @Test
        void responseWithoutPricesIsNotLookedUp() {
            callerIs("MECHANIC");
            when(response.getEntity()).thenReturn(Map.of("message", "ok"));

            redact();

            verify(response, never()).setEntity(any());
            verifyNoInteractions(priceVisibility);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"TALLER_ADMIN", "SYSTEM_ADMIN", "OWNER"})
        void adminsAndOwnersAreNeverRedactedNorLookedUp(String role) {
            callerIs(role);
            when(response.getEntity()).thenReturn(ORDER);

            redact();

            verify(response, never()).setEntity(any());
            verifyNoInteractions(priceVisibility);
        }

        private void redact() {
            filter.redactPrices(response).await().indefinitely();
        }
    }

    @Nested
    class RejectFinancialEndpoints {

        @Test
        void financialEndpointIsForbiddenToMechanicWithoutPermission() throws NoSuchMethodException {
            callerIs("MECHANIC");
            mechanicMayViewPrices(false);
            endpointIs(Endpoints.class.getDeclaredMethod("financial"));

            assertEquals(403, reject().getStatus());
        }

        @Test
        void financialEndpointIsOpenToMechanicWithPermission() throws NoSuchMethodException {
            callerIs("MECHANIC");
            mechanicMayViewPrices(true);
            endpointIs(Endpoints.class.getDeclaredMethod("financial"));

            assertNull(reject());
        }

        @Test
        void financialEndpointIsOpenToAdmins() throws NoSuchMethodException {
            callerIs("TALLER_ADMIN");
            endpointIs(Endpoints.class.getDeclaredMethod("financial"));

            assertNull(reject());
            verifyNoInteractions(priceVisibility);
        }

        @Test
        void wholeFinancialResourceIsForbiddenToo() throws NoSuchMethodException {
            callerIs("MECHANIC");
            mechanicMayViewPrices(false);
            endpointIs(FinancialEndpoints.class.getDeclaredMethod("report"));
            doReturn(FinancialEndpoints.class).when(resource).getResourceClass();

            assertEquals(403, reject().getStatus());
        }

        @Test
        void ordinaryEndpointIsNotRejected() throws NoSuchMethodException {
            callerIs("MECHANIC");
            endpointIs(Endpoints.class.getDeclaredMethod("ordinary"));
            doReturn(Endpoints.class).when(resource).getResourceClass();

            assertNull(reject());
            verifyNoInteractions(priceVisibility);
        }

        private Response reject() {
            return filter.rejectFinancialEndpoints(request, resource).await().indefinitely();
        }
    }

    private void callerIs(String role) {
        filter.identity = Identities.withRole(role);
    }

    private void mechanicMayViewPrices(boolean canViewPrices) {
        when(priceVisibility.canViewPrices(any(SecurityIdentity.class), any(JsonWebToken.class)))
                .thenReturn(Uni.createFrom().item(canViewPrices));
    }

    private void endpointIs(Method method) {
        when(resource.getResourceMethod()).thenReturn(method);
    }

    record PricedOrder(String name, Double price) {
    }

    static class OrderRedactor implements PriceRedactor {

        @Override
        public boolean carriesPrices(Object entity) {
            return entity instanceof PricedOrder;
        }

        @Override
        public Object redact(Object entity) {
            return entity instanceof PricedOrder order ? new PricedOrder(order.name(), null) : entity;
        }
    }

    static class Endpoints {
        @RequiresPriceVisibility
        void financial() {
        }

        void ordinary() {
        }
    }

    @RequiresPriceVisibility
    static class FinancialEndpoints {
        void report() {
        }
    }
}
