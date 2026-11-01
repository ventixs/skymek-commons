package app.skymek.commons.support;

import app.skymek.commons.testsupport.Identities;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportModeFilterTest {

    @Mock
    ContainerRequestContext request;

    @Mock
    UriInfo uriInfo;

    @Test
    void requestOutsideASupportSessionIsNotChecked() {
        assertTrue(enforce(Identities.withRole("TALLER_ADMIN")).isEmpty());
        verifyNoInteractions(request);
    }

    @Nested
    class InsideASession {

        @ParameterizedTest(name = "{0} session, {1}: allowed={2}")
        @CsvSource({
                "read,   GET,    true",
                "read,   POST,   false",
                "denied, GET,    true",
                "denied, DELETE, false",
                "write,  DELETE, true"
        })
        void readOnlyAndDeniedSessionsOnlyRead(String kind, String method, boolean allowed) {
            when(request.getMethod()).thenReturn(method);
            when(request.getUriInfo()).thenReturn(uriInfo);
            when(uriInfo.getPath()).thenReturn("/talleres/42");

            assertEquals(allowed, enforce(session(kind)).isEmpty());
        }

        @Test
        void refusalKeepsTheBodyTheFrontendMatchesOn() {
            when(request.getMethod()).thenReturn("POST");
            when(request.getUriInfo()).thenReturn(uriInfo);
            when(uriInfo.getPath()).thenReturn("/talleres/42");

            Response refused = enforce(session("read")).orElseThrow();

            assertEquals(403, refused.getStatus());
            assertEquals("SUPPORT_MODE", ((Map<?, ?>) refused.getEntity()).get("error"));
        }

        private SecurityIdentity session(String kind) {
            return switch (kind) {
                case "read" -> Identities.inSupportSession("s-1", "TALLER_ADMIN", SupportIdentities.READ_ONLY_ROLE);
                case "write" -> Identities.inSupportSession("s-1", "TALLER_ADMIN", SupportIdentities.WRITE_ROLE);
                default -> Identities.inSupportSession("denied");
            };
        }
    }

    private Optional<Response> enforce(SecurityIdentity identity) {
        SupportModeFilter filter = new SupportModeFilter();
        filter.identity = identity;
        return filter.enforce(request);
    }
}
