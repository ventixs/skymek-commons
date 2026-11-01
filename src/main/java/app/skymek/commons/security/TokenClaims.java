package app.skymek.commons.security;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Reads the tenant claims of a SkyMek access token. Ids may arrive as numbers or as strings, quoted or
 * not, depending on the Keycloak mapper; anything that is not a whole number reads as absent.
 */
public final class TokenClaims {

    /** The claim the Keycloak protocol mapper emits for the caller's taller. */
    public static final String WORKSHOP_ID = "workshopId";
    /** Fallback taller claim, for tokens minted by other paths. */
    public static final String TALLER_ID = "tallerId";
    public static final String OWNER_ID = "ownerId";
    public static final String EMAIL = "email";

    private TokenClaims() {
    }

    public static Long tallerId(JsonWebToken jwt) {
        if (jwt == null) {
            return null;
        }
        Long workshop = id(jwt.getClaim(WORKSHOP_ID));
        return workshop != null ? workshop : id(jwt.getClaim(TALLER_ID));
    }

    public static Long ownerId(JsonWebToken jwt) {
        return jwt == null ? null : id(jwt.getClaim(OWNER_ID));
    }

    /** The Keycloak subject, or null for a missing token or a blank subject. */
    public static String subject(JsonWebToken jwt) {
        if (jwt == null) {
            return null;
        }
        String subject = jwt.getSubject();
        return subject == null || subject.isBlank() ? null : subject;
    }

    /** A claim as plain text, without the quotes a JSON string value keeps in its {@code toString}. */
    public static String text(JsonWebToken jwt, String name) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaim(name);
        return value == null ? null : value.toString().replace("\"", "");
    }

    public static Long id(Object claim) {
        if (claim == null) {
            return null;
        }
        if (claim instanceof Number number) {
            return number.longValue();
        }
        String text = claim.toString().replace("\"", "").trim();
        try {
            return text.isEmpty() ? null : Long.valueOf(text);
        } catch (NumberFormatException notAnId) {
            return null;
        }
    }
}
