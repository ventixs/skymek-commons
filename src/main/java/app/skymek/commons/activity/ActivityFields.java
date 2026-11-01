package app.skymek.commons.activity;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ResourceInfo;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the stream entry for one write. Same file in every service that publishes activity,
 * so the consumer in ms-data reads one format.
 */
public final class ActivityFields {

    public static final String STREAM_KEY = "skymek:activity";
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> ROLE_PRIORITY = List.of("SYSTEM_ADMIN", "TALLER_ADMIN", "MECHANIC", "VEHICLE_OWNER", "OWNER");

    private ActivityFields() {
    }

    public static boolean isRecordable(String method, int status, String routeTemplate) {
        return WRITES.contains(method) && status >= 200 && status < 300
                && routeTemplate != null && !routeTemplate.startsWith("/internal");
    }

    public static String routeTemplate(ResourceInfo resource) {
        if (resource == null || resource.getResourceClass() == null || resource.getResourceMethod() == null) {
            return null;
        }
        Path classPath = resource.getResourceClass().getAnnotation(Path.class);
        Path methodPath = resource.getResourceMethod().getAnnotation(Path.class);
        String joined = (classPath == null ? "" : classPath.value()) + (methodPath == null ? "" : "/" + methodPath.value());
        String normalized = ("/" + joined).replaceAll("/+", "/");
        return normalized.length() > 1 && normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    public static String primaryRole(Collection<String> roles) {
        return ROLE_PRIORITY.stream().filter(roles::contains)
                .map(role -> "VEHICLE_OWNER".equals(role) ? "OWNER" : role).findFirst().orElse(null);
    }

    public static Map<String, String> entry(String service, Instant occurredAt, String method, String routeTemplate,
                                            int status, Long tallerId, Long ownerId, String actorSub,
                                            String actorEmail, String actorRole, String supportSessionId) {
        Map<String, String> fields = new HashMap<>();
        fields.put("service", service);
        fields.put("occurredAt", occurredAt.toString());
        fields.put("method", method);
        fields.put("route", routeTemplate);
        fields.put("status", Integer.toString(status));
        putIfPresent(fields, "tallerId", tallerId);
        putIfPresent(fields, "ownerId", ownerId);
        putIfPresent(fields, "actorSub", actorSub);
        putIfPresent(fields, "actorEmail", actorEmail);
        putIfPresent(fields, "actorRole", actorRole);
        putIfPresent(fields, "supportSessionId", supportSessionId);
        return fields;
    }

    public static Long firstId(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return Long.valueOf(candidate.trim());
            } catch (NumberFormatException notAnId) {
                continue;
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, String> fields, String key, Object value) {
        if (value != null) {
            fields.put(key, value.toString());
        }
    }
}
