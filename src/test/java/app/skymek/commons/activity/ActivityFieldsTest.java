package app.skymek.commons.activity;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ResourceInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ActivityFieldsTest {

    @Nested
    class IsRecordable {

        @ParameterizedTest(name = "{0} {1} {2} -> {3}")
        @CsvSource({
                "POST, 201, /orders, true",
                "DELETE, 204, /orders/{id}, true",
                "GET, 200, /orders, false",
                "POST, 400, /orders, false",
                "POST, 201, /internal/sync, false"
        })
        void recordsOnlySuccessfulPublicWrites(String method, int status, String route, boolean expected) {
            assertEquals(expected, ActivityFields.isRecordable(method, status, route));
        }

        @Test
        void ignoresMissingRoute() {
            assertFalse(ActivityFields.isRecordable("POST", 201, null));
        }
    }

    @Nested
    class RouteTemplate {

        @Path("/orders/")
        static class OrdersResource {
            @Path("{id}")
            public void byId() {
            }
        }

        @Test
        void joinsClassAndMethodPaths() throws NoSuchMethodException {
            Method method = OrdersResource.class.getMethod("byId");
            ResourceInfo info = mock(ResourceInfo.class);
            doReturn(OrdersResource.class).when(info).getResourceClass();
            doReturn(method).when(info).getResourceMethod();

            assertEquals("/orders/{id}", ActivityFields.routeTemplate(info));
        }

        @Test
        void returnsNullWithoutResource() {
            assertNull(ActivityFields.routeTemplate(null));
        }
    }

    @Nested
    class PrimaryRole {

        @Test
        void picksTheHighestRoleAndNamesVehicleOwnersAsOwner() {
            assertEquals("TALLER_ADMIN", ActivityFields.primaryRole(List.of("MECHANIC", "TALLER_ADMIN")));
            assertEquals("OWNER", ActivityFields.primaryRole(List.of("VEHICLE_OWNER")));
            assertNull(ActivityFields.primaryRole(List.of("offline_access")));
        }
    }

    @Nested
    class Entry {

        @Test
        void leavesOutAbsentOptionalFields() {
            Map<String, String> fields = ActivityFields.entry("ms-data", Instant.parse("2026-11-01T00:00:00Z"),
                    "POST", "/orders", 201, 7L, null, "sub-1", null, "TALLER_ADMIN", null);

            assertEquals("7", fields.get("tallerId"));
            assertFalse(fields.containsKey("ownerId"));
            assertFalse(fields.containsKey("supportSessionId"));
            assertEquals("2026-11-01T00:00:00Z", fields.get("occurredAt"));
        }

        @Test
        void firstIdSkipsBlankAndNonNumericCandidates() {
            assertEquals(42L, ActivityFields.firstId(null, " ", "abc", "42"));
            assertNull(ActivityFields.firstId("x"));
        }
    }
}
