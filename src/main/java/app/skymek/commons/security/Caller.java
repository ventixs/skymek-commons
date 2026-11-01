package app.skymek.commons.security;

/**
 * The caller's identity taken once per check, so the decision can run in a later callback without
 * touching request-scoped beans.
 *
 * @param tallerId the taller of a staff member (admin or mechanic); null for anyone else
 * @param ownerId  the owner of a vehicle owner; null for anyone else
 */
public record Caller(boolean systemAdmin, Long tallerId, Long ownerId) {

    public static Caller systemAdministrator() {
        return new Caller(true, null, null);
    }

    public static Caller staffOf(Long tallerId) {
        return new Caller(false, tallerId, null);
    }

    public static Caller owner(Long ownerId) {
        return new Caller(false, null, ownerId);
    }

    public static Caller nobody() {
        return new Caller(false, null, null);
    }

    public boolean tallerStaff() {
        return tallerId != null;
    }

    public boolean vehicleOwner() {
        return ownerId != null;
    }

    public boolean memberOf(Long taller) {
        return taller != null && taller.equals(tallerId);
    }

    public boolean mayActOnTaller(Long taller) {
        return systemAdmin || memberOf(taller);
    }
}
