package app.skymek.commons.client;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthHeadersFactoryTest {

    @Mock
    HttpServerRequest request;

    @InjectMocks
    AuthHeadersFactory factory;

    @Test
    void bearerAndSupportSessionTravelFromTheIncomingHeaders() {
        MultivaluedMap<String, String> incoming = headers("Authorization", "Bearer t");
        incoming.putSingle("X-Support-Session", "s-1");

        MultivaluedMap<String, String> outgoing = factory.update(incoming, headers("Accept", "application/json"));

        assertEquals("Bearer t", outgoing.getFirst("Authorization"));
        assertEquals("s-1", outgoing.getFirst("X-Support-Session"));
        assertEquals("application/json", outgoing.getFirst("Accept"));
        verifyNoInteractions(request);
    }

    @Test
    void headersMissingFromTheContextAreReadFromTheHttpRequest() {
        when(request.getHeader("Authorization")).thenReturn("Bearer t");
        when(request.getHeader("X-Support-Session")).thenReturn("s-1");

        MultivaluedMap<String, String> outgoing = factory.update(new MultivaluedHashMap<>(), new MultivaluedHashMap<>());

        assertEquals("Bearer t", outgoing.getFirst("Authorization"));
        assertEquals("s-1", outgoing.getFirst("X-Support-Session"));
    }

    @Test
    void blankHeadersAreNotForwarded() {
        MultivaluedMap<String, String> incoming = headers("Authorization", "");
        incoming.putSingle("X-Support-Session", " ");

        MultivaluedMap<String, String> outgoing = factory.update(incoming, new MultivaluedHashMap<>());

        assertFalse(outgoing.containsKey("Authorization"));
        assertFalse(outgoing.containsKey("X-Support-Session"));
    }

    private static MultivaluedMap<String, String> headers(String name, String value) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle(name, value);
        return headers;
    }
}
