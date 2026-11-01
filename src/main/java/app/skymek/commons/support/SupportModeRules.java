package app.skymek.commons.support;

/**
 * What a support session may send. «Solo ver» only reads; «Configurar» may do everything the client
 * could, including plan, users, payments and deleting (Carlos, 2026-11-01: the super admin tests the
 * whole platform from there). Every session stays on record in support_session.
 */
public final class SupportModeRules {

    private SupportModeRules() {
    }

    public static boolean allows(boolean readOnly, String method, String path) {
        return !readOnly || "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
