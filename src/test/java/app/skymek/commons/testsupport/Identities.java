package app.skymek.commons.testsupport;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;

import java.security.Principal;

public final class Identities {

    private static final Principal USER = () -> "user";

    private Identities() {
    }

    public static SecurityIdentity withRole(String role) {
        return withRoles(role);
    }

    public static SecurityIdentity withRoles(String... roles) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder().setPrincipal(USER);
        for (String role : roles) {
            builder.addRole(role);
        }
        return builder.build();
    }

    /** An identity inside the support session {@code sessionId} ("denied" when the augmentor refused it). */
    public static SecurityIdentity inSupportSession(String sessionId, String... roles) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder().setPrincipal(USER)
                .addAttribute("supportSessionId", sessionId);
        for (String role : roles) {
            builder.addRole(role);
        }
        return builder.build();
    }
}
