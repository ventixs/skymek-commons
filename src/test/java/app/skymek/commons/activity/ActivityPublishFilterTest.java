package app.skymek.commons.activity;

import app.skymek.commons.testsupport.Identities;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.ReactiveStreamCommands;
import io.quarkus.redis.datasource.stream.XAddArgs;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityPublishFilterTest {

    @Mock
    ReactiveRedisDataSource redis;

    @Mock
    ReactiveStreamCommands<String, String, String> stream;

    @Mock
    JsonWebToken jwt;

    @Mock
    ContainerRequestContext request;

    @Mock
    ContainerResponseContext response;

    @Mock
    ResourceInfo resource;

    @Mock
    UriInfo uriInfo;

    @Test
    void anonymousWritesAreNotPublished(@Mock SecurityIdentity anonymous) throws NoSuchMethodException {
        when(anonymous.isAnonymous()).thenReturn(true);
        endpointIs("create");

        publish(anonymous);

        verifyNoInteractions(redis);
    }

    @Nested
    class AuthenticatedWrite {

        @Test
        void failedWriteIsNotPublished() throws NoSuchMethodException {
            endpointIs("create");
            when(request.getMethod()).thenReturn("POST");
            when(response.getStatus()).thenReturn(400);

            publish(Identities.withRole("TALLER_ADMIN"));

            verifyNoInteractions(redis);
        }

        @Test
        void successfulWriteIsPublishedUnderTheConfiguredServiceWithTheTokenTaller() throws NoSuchMethodException {
            endpointIs("create");
            when(request.getMethod()).thenReturn("POST");
            when(response.getStatus()).thenReturn(201);
            when(request.getUriInfo()).thenReturn(uriInfo);
            when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
            when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
            when(jwt.getClaim(anyString())).thenAnswer(claim -> "workshopId".equals(claim.getArgument(0)) ? 42L : null);
            when(jwt.getSubject()).thenReturn("user-sub");
            doReturn(stream).when(redis).stream(String.class);
            when(stream.xadd(eq(ActivityFields.STREAM_KEY), any(XAddArgs.class), any()))
                    .thenReturn(Uni.createFrom().item("1-0"));

            publish(Identities.withRole("TALLER_ADMIN"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> entry = ArgumentCaptor.forClass(Map.class);
            verify(stream).xadd(eq(ActivityFields.STREAM_KEY), any(XAddArgs.class), entry.capture());
            assertEquals("ms-test", entry.getValue().get("service"));
            assertEquals("42", entry.getValue().get("tallerId"));
            assertEquals("/orders", entry.getValue().get("route"));
            assertEquals("TALLER_ADMIN", entry.getValue().get("actorRole"));
            assertFalse(entry.getValue().containsKey("supportSessionId"));
        }
    }

    private void endpointIs(String method) throws NoSuchMethodException {
        doReturn(Orders.class).when(resource).getResourceClass();
        when(resource.getResourceMethod()).thenReturn(Orders.class.getDeclaredMethod(method));
    }

    private void publish(SecurityIdentity identity) {
        ActivityPublishFilter filter = new ActivityPublishFilter();
        filter.identity = identity;
        filter.jwt = jwt;
        filter.redis = redis;
        filter.service = "ms-test";
        filter.publishWrite(request, response, resource);
    }

    @Path("/orders")
    static class Orders {
        @POST
        void create() {
        }
    }
}
