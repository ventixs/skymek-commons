package app.skymek.commons.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class HybridCacheServiceTest {

    private static final String PREFIX = "cache:test:";
    private static final String CACHE = "catalog";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<String>> LIST = new TypeReference<>() { };

    @Mock
    ReactiveRedisDataSource redis;

    @Mock
    ReactiveValueCommands<String, String> values;

    @Mock
    ReactiveKeyCommands<String> keys;

    private HybridCacheService cache;

    @BeforeEach
    void cache() {
        cache = new HybridCacheService();
        cache.redisDataSource = redis;
        cache.objectMapper = new ObjectMapper();
        cache.keyPrefix = PREFIX;
        cache.l1Enabled = true;
        cache.l1MaxSize = 100;
        cache.l1TtlAfterL2Hit = Duration.ofMinutes(30);
        cache.l2Enabled = true;
    }

    private void valuesAvailable() {
        doReturn(values).when(redis).value(String.class, String.class);
    }

    private void keysAvailable() {
        doReturn(keys).when(redis).key(String.class);
    }

    private void redisAcceptsWrites() {
        valuesAvailable();
        when(values.setex(anyString(), anyLong(), anyString())).thenReturn(Uni.createFrom().voidItem());
    }

    private static <T> T await(Uni<T> uni) {
        return uni.await().indefinitely();
    }

    @Nested
    class RedisKeys {

        @Test
        void areThePrefixTheCacheNameAndTheKey() {
            redisAcceptsWrites();

            await(cache.put(CACHE, "brands", List.of("Toyota"), TTL));

            verify(values).setex("cache:test:catalog:brands", 300L, "[\"Toyota\"]");
        }

        @Test
        void followTheConfiguredPrefix() {
            cache.keyPrefix = "cache:hybrid:";
            redisAcceptsWrites();

            await(cache.put("prediction", "vehicle:7", "ok", TTL));

            verify(values).setex("cache:hybrid:prediction:vehicle:7", 300L, "\"ok\"");
        }
    }

    @Nested
    class Get {

        @Test
        void l1HitDoesNotTouchRedis() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "brands", List.of("Toyota"), TTL));
            cache.l2Enabled = true;

            assertEquals(List.of("Toyota"), await(cache.get(CACHE, "brands", LIST)));
            verifyNoInteractions(redis);
        }

        @Test
        void l2HitIsDeserializedAndPromotedToL1() {
            valuesAvailable();
            when(values.get("cache:test:catalog:brands")).thenReturn(Uni.createFrom().item("[\"Kia\"]"));

            assertEquals(List.of("Kia"), await(cache.get(CACHE, "brands", LIST)));
            assertEquals(List.of("Kia"), cache.<List<String>>peekL1(CACHE, "brands"));
        }

        @Test
        void missInEveryLevelEmitsNull() {
            valuesAvailable();
            when(values.get("cache:test:catalog:brands")).thenReturn(Uni.createFrom().nullItem());

            assertNull(await(cache.get(CACHE, "brands", LIST)));
        }

        @Test
        void unreadableL2EntryEmitsNull() {
            valuesAvailable();
            when(values.get("cache:test:catalog:brands")).thenReturn(Uni.createFrom().item("not json"));

            assertNull(await(cache.get(CACHE, "brands", LIST)));
            assertNull(cache.peekL1(CACHE, "brands"));
        }

        @Test
        void disabledL2IsNeverQueried() {
            cache.l2Enabled = false;

            assertNull(await(cache.get(CACHE, "brands", LIST)));
            verifyNoInteractions(redis);
        }
    }

    @Nested
    class GetOrCompute {

        @Test
        void cachedValueSkipsTheLoader() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "brands", List.of("Toyota"), TTL));
            AtomicInteger loads = new AtomicInteger();

            List<String> result = await(cache.getOrCompute(CACHE, "brands", LIST, TTL, () -> {
                loads.incrementAndGet();
                return Uni.createFrom().item(List.of("other"));
            }));

            assertEquals(List.of("Toyota"), result);
            assertEquals(0, loads.get());
        }

        @Test
        void missLoadsAndCachesInBothLevels() {
            redisAcceptsWrites();
            when(values.get("cache:test:catalog:brands")).thenReturn(Uni.createFrom().nullItem());

            List<String> result = await(cache.getOrCompute(CACHE, "brands", LIST, TTL,
                    () -> Uni.createFrom().item(List.of("Kia"))));

            assertEquals(List.of("Kia"), result);
            verify(values).setex("cache:test:catalog:brands", 300L, "[\"Kia\"]");
            assertEquals(List.of("Kia"), cache.<List<String>>peekL1(CACHE, "brands"));
        }

        @Test
        void emptyCollectionIsReturnedButNotCached() {
            valuesAvailable();
            when(values.get("cache:test:catalog:brands")).thenReturn(Uni.createFrom().nullItem());

            List<String> result = await(cache.getOrCompute(CACHE, "brands", LIST, TTL,
                    () -> Uni.createFrom().item(List.of())));

            assertTrue(result.isEmpty());
            verify(values, never()).setex(anyString(), anyLong(), anyString());
            assertNull(cache.peekL1(CACHE, "brands"));
        }

        @Test
        void emptyMapIsReturnedButNotCached() {
            cache.l2Enabled = false;

            Map<String, Integer> result = await(cache.getOrCompute(CACHE, "counts",
                    new TypeReference<Map<String, Integer>>() { }, TTL, () -> Uni.createFrom().item(Map.of())));

            assertTrue(result.isEmpty());
            assertNull(cache.peekL1(CACHE, "counts"));
        }

        @Test
        void nullIsReturnedButNotCached() {
            cache.l2Enabled = false;

            assertNull(await(cache.getOrCompute(CACHE, "brands", LIST, TTL, () -> Uni.createFrom().nullItem())));
            assertEquals(0, cache.getStats().l1Size());
        }
    }

    @Nested
    class PeekL1 {

        @Test
        void returnsWhatWasPutWithoutTouchingRedis() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "1", "vehicle-1", TTL));

            assertEquals("vehicle-1", cache.<String>peekL1(CACHE, "1"));
            verifyNoInteractions(redis);
        }

        @Test
        void missReturnsNull() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "1", "vehicle-1", TTL));

            assertNull(cache.peekL1(CACHE, "2"));
            assertNull(cache.peekL1("owners", "1"));
        }

        @Test
        void expiredEntryReturnsNull() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "1", "vehicle-1", Duration.ofMillis(-1)));

            assertNull(cache.peekL1(CACHE, "1"));
        }

        @Test
        void disabledL1ReturnsNull() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "1", "vehicle-1", TTL));
            cache.l1Enabled = false;

            assertNull(cache.peekL1(CACHE, "1"));
        }
    }

    @Nested
    class Invalidate {

        @Test
        void removesTheEntryFromBothLevels() {
            redisAcceptsWrites();
            keysAvailable();
            when(keys.del("cache:test:catalog:brands")).thenReturn(Uni.createFrom().item(1));
            await(cache.put(CACHE, "brands", List.of("Toyota"), TTL));

            await(cache.invalidate(CACHE, "brands"));

            assertNull(cache.peekL1(CACHE, "brands"));
            verify(keys).del("cache:test:catalog:brands");
        }
    }

    @Nested
    class InvalidateAll {

        @Test
        void removesOnlyThatCacheFromL1AndItsKeysFromRedis() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "brands", "a", TTL));
            await(cache.put("catalogs", "brands", "b", TTL));
            cache.l2Enabled = true;
            keysAvailable();
            when(keys.keys("cache:test:catalog:*"))
                    .thenReturn(Uni.createFrom().item(List.of("cache:test:catalog:brands")));
            when(keys.del("cache:test:catalog:brands")).thenReturn(Uni.createFrom().item(1));

            await(cache.invalidateAll(CACHE));

            assertNull(cache.peekL1(CACHE, "brands"));
            assertEquals("b", cache.<String>peekL1("catalogs", "brands"));
            verify(keys).del("cache:test:catalog:brands");
        }

        @Test
        void noMatchingKeysDeletesNothing() {
            keysAvailable();
            when(keys.keys("cache:test:catalog:*")).thenReturn(Uni.createFrom().item(List.of()));

            await(cache.invalidateAll(CACHE));

            verify(keys, never()).del(any(String[].class));
        }
    }

    @Nested
    class L1Capacity {

        @Test
        void evictsWhenFull() {
            cache.l2Enabled = false;
            cache.l1MaxSize = 10;
            for (int i = 0; i < 10; i++) {
                await(cache.put(CACHE, String.valueOf(i), i, TTL));
            }

            await(cache.put(CACHE, "new", 99, TTL));

            assertEquals(10, cache.getStats().l1Size());
            assertEquals(99, cache.<Integer>peekL1(CACHE, "new"));
        }
    }

    @Nested
    class Monitoring {

        @Test
        void statsReportSizeAndLevels() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "brands", "a", TTL));

            assertEquals(new CacheStats(1, true, false), cache.getStats());
        }

        @Test
        void clearL1CacheEmptiesMemoryOnly() {
            cache.l2Enabled = false;
            await(cache.put(CACHE, "brands", "a", TTL));

            cache.clearL1Cache();

            assertEquals(0, cache.getStats().l1Size());
            assertFalse(cache.getStats().l2Enabled());
        }
    }
}
