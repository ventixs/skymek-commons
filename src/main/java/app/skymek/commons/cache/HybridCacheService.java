package app.skymek.commons.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Two-level cache: a per-instance in-memory map (L1) in front of Redis (L2).
 * Reads check L1, then L2 (promoting the hit into L1); writes go to both; invalidation clears both.
 * Every Redis key is {@code <key-prefix><cacheName>:<key>}, with the prefix from {@value #KEY_PREFIX_KEY}.
 * Null values and empty collections or maps are never cached.
 */
@ApplicationScoped
public class HybridCacheService {

    public static final String KEY_PREFIX_KEY = "skymek.cache.key-prefix";

    private static final Logger LOG = Logger.getLogger(HybridCacheService.class);

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = KEY_PREFIX_KEY)
    String keyPrefix;

    @ConfigProperty(name = "skymek.cache.l1.enabled", defaultValue = "true")
    boolean l1Enabled;

    @ConfigProperty(name = "skymek.cache.l1.max-size", defaultValue = "1000")
    int l1MaxSize;

    @ConfigProperty(name = "skymek.cache.l1.ttl-after-l2-hit", defaultValue = "PT30M")
    Duration l1TtlAfterL2Hit;

    @ConfigProperty(name = "skymek.cache.l2.enabled", defaultValue = "true")
    boolean l2Enabled;

    private final Map<String, CacheEntry> l1Cache = new ConcurrentHashMap<>();

    private ReactiveValueCommands<String, String> valueCommands;
    private ReactiveKeyCommands<String> keyCommands;

    @PostConstruct
    void init() {
        LOG.infof("HybridCacheService initialized - prefix: %s, L1: %s, L2: %s, L1 max size: %d",
                keyPrefix, l1Enabled ? "enabled" : "disabled", l2Enabled ? "enabled" : "disabled", l1MaxSize);
    }

    /**
     * Reads L1, then L2; emits null on a miss in every enabled level.
     */
    public <T> Uni<T> get(String cacheName, String key, TypeReference<T> typeRef) {
        String fullKey = buildKey(cacheName, key);

        T fromL1 = readL1(fullKey);
        if (fromL1 != null) {
            LOG.debugf("L1 cache HIT: %s", fullKey);
            return Uni.createFrom().item(fromL1);
        }

        if (l2Enabled) {
            return valueCommands().get(fullKey)
                    .map(json -> json == null ? null : deserializeAndPromote(fullKey, json, typeRef))
                    .invoke(value -> {
                        if (value == null) {
                            LOG.debugf("L2 cache MISS: %s", fullKey);
                        }
                    });
        }

        LOG.debugf("Cache MISS (all levels): %s", fullKey);
        return Uni.createFrom().nullItem();
    }

    /**
     * Reads only the in-memory level and never blocks, so batch lookups can use it on the event loop.
     * Returns null on a miss, an expired entry or when L1 is disabled.
     */
    public <T> T peekL1(String cacheName, String key) {
        return readL1(buildKey(cacheName, key));
    }

    /**
     * Returns the cached value or loads, caches and returns it; null and empty results are returned uncached.
     */
    public <T> Uni<T> getOrCompute(String cacheName, String key, TypeReference<T> typeRef,
                                   Duration ttl, Supplier<Uni<T>> loader) {
        return get(cacheName, key, typeRef)
                .chain(cached -> {
                    if (cached != null) {
                        return Uni.createFrom().item(cached);
                    }
                    return loader.get().chain(value -> isCacheable(value)
                            ? put(cacheName, key, value, ttl).replaceWith(value)
                            : Uni.createFrom().item(value));
                });
    }

    /**
     * Writes the value to both levels with the given TTL.
     */
    public <T> Uni<Void> put(String cacheName, String key, T value, Duration ttl) {
        String fullKey = buildKey(cacheName, key);

        if (l1Enabled) {
            putL1(fullKey, value, ttl);
        }

        if (!l2Enabled) {
            return Uni.createFrom().voidItem();
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            LOG.errorf("Failed to serialize for L2 cache: %s - %s", fullKey, e.getMessage());
            return Uni.createFrom().voidItem();
        }
        return valueCommands().setex(fullKey, ttl.getSeconds(), json)
                .invoke(() -> LOG.debugf("L2 cache PUT: %s (TTL: %ds)", fullKey, ttl.getSeconds()))
                .onFailure().invoke(e -> LOG.errorf("Failed to put in L2 cache: %s - %s", fullKey, e.getMessage()))
                .replaceWithVoid();
    }

    /**
     * Removes one entry from both levels.
     */
    public Uni<Void> invalidate(String cacheName, String key) {
        String fullKey = buildKey(cacheName, key);

        if (l1Enabled) {
            l1Cache.remove(fullKey);
            LOG.debugf("L1 cache INVALIDATE: %s", fullKey);
        }

        if (l2Enabled) {
            return keyCommands().del(fullKey)
                    .invoke(() -> LOG.debugf("L2 cache INVALIDATE: %s", fullKey))
                    .replaceWithVoid();
        }

        return Uni.createFrom().voidItem();
    }

    /**
     * Removes every entry of a cache from both levels. {@code cacheName} may end in {@code *} to match
     * several caches, since it is used as a Redis glob.
     */
    public Uni<Void> invalidateAll(String cacheName) {
        String prefix = keyPrefix + cacheName + ":";

        if (l1Enabled) {
            l1Cache.keySet().removeIf(k -> k.startsWith(prefix));
            LOG.debugf("L1 cache INVALIDATE ALL: %s", cacheName);
        }

        if (l2Enabled) {
            return keyCommands().keys(prefix + "*")
                    .chain(keys -> {
                        if (keys.isEmpty()) {
                            return Uni.createFrom().voidItem();
                        }
                        LOG.infof("L2 cache INVALIDATE ALL: %s (%d keys)", cacheName, keys.size());
                        return keyCommands().del(keys.toArray(new String[0])).replaceWithVoid();
                    });
        }

        return Uni.createFrom().voidItem();
    }

    public CacheStats getStats() {
        return new CacheStats(l1Cache.size(), l1Enabled, l2Enabled);
    }

    public void clearL1Cache() {
        l1Cache.clear();
        LOG.info("L1 cache cleared");
    }

    private String buildKey(String cacheName, String key) {
        return keyPrefix + cacheName + ":" + key;
    }

    @SuppressWarnings("unchecked")
    private <T> T readL1(String fullKey) {
        if (!l1Enabled) {
            return null;
        }
        CacheEntry entry = l1Cache.get(fullKey);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return (T) entry.value();
    }

    private <T> T deserializeAndPromote(String fullKey, String json, TypeReference<T> typeRef) {
        try {
            T value = objectMapper.readValue(json, typeRef);
            LOG.debugf("L2 cache HIT: %s", fullKey);
            if (l1Enabled && value != null) {
                putL1(fullKey, value, l1TtlAfterL2Hit);
            }
            return value;
        } catch (JsonProcessingException e) {
            LOG.errorf("Failed to deserialize from L2 cache: %s - %s", fullKey, e.getMessage());
            return null;
        }
    }

    private static boolean isCacheable(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private void putL1(String fullKey, Object value, Duration ttl) {
        if (l1Cache.size() >= l1MaxSize) {
            l1Cache.entrySet().removeIf(e -> e.getValue().isExpired());
            if (l1Cache.size() >= l1MaxSize) {
                l1Cache.keySet().stream().limit(Math.max(1, l1MaxSize / 10)).forEach(l1Cache::remove);
            }
        }
        l1Cache.put(fullKey, new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis()));
        LOG.debugf("L1 cache PUT: %s (TTL: %ds)", fullKey, ttl.getSeconds());
    }

    private ReactiveValueCommands<String, String> valueCommands() {
        if (valueCommands == null) {
            valueCommands = redisDataSource.value(String.class, String.class);
        }
        return valueCommands;
    }

    private ReactiveKeyCommands<String> keyCommands() {
        if (keyCommands == null) {
            keyCommands = redisDataSource.key(String.class);
        }
        return keyCommands;
    }

    private record CacheEntry(Object value, long expiresAt) {

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
