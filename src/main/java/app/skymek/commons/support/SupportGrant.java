package app.skymek.commons.support;

import java.time.Instant;

/**
 * An active support session as every service sees it.
 *
 * @param targetType TALLER or OWNER
 * @param mode       READ or WRITE
 */
public record SupportGrant(String sessionId, String adminSub, String targetType, Long targetId, String mode,
                           Instant expiresAt) {

    public static final String TALLER = "TALLER";
    public static final String OWNER = "OWNER";
    public static final String READ = "READ";
    public static final String WRITE = "WRITE";

    public boolean readOnly() {
        return !WRITE.equals(mode);
    }

    public boolean isUsableBy(String sub, Instant now) {
        return adminSub != null && adminSub.equals(sub) && expiresAt != null && now.isBefore(expiresAt)
                && (TALLER.equals(targetType) || OWNER.equals(targetType)) && targetId != null;
    }
}
