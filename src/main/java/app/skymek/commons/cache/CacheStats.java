package app.skymek.commons.cache;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Snapshot of the hybrid cache for monitoring endpoints.
 */
@RegisterForReflection
public record CacheStats(int l1Size, boolean l1Enabled, boolean l2Enabled) {
}
