package app.skymek.commons.client;

import app.skymek.commons.support.SupportIdentities;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Forwards the caller's {@code Authorization} header to service-to-service REST calls, and the support
 * session header with it, so the downstream service answers for the taller or owner the admin acts as and
 * not for the admin's own token. Register it with {@code @RegisterClientHeaders(AuthHeadersFactory.class)}.
 */
@ApplicationScoped
public class AuthHeadersFactory implements ClientHeadersFactory {

    static final String AUTHORIZATION = "Authorization";

    @Inject
    HttpServerRequest request;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                 MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>(clientOutgoingHeaders);
        forward(AUTHORIZATION, incomingHeaders, result);
        forward(SupportIdentities.HEADER, incomingHeaders, result);
        return result;
    }

    private void forward(String header, MultivaluedMap<String, String> incomingHeaders,
                         MultivaluedMap<String, String> result) {
        String value = incomingHeaders.getFirst(header);
        if (value == null && request != null) {
            value = request.getHeader(header);
        }
        if (value != null && !value.isBlank()) {
            result.putSingle(header, value);
        }
    }
}
