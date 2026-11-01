package app.skymek.commons.security;

/** Keycloak realm roles, as constants so they can be used in {@code @RolesAllowed}. */
public final class Roles {

    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String TALLER_ADMIN = "TALLER_ADMIN";
    public static final String MECHANIC = "MECHANIC";
    public static final String VEHICLE_OWNER = "VEHICLE_OWNER";
    /** Older name of {@link #VEHICLE_OWNER}, still accepted wherever an owner is recognised. */
    public static final String OWNER = "OWNER";

    private Roles() {
    }
}
