package app.skymek.commons.support;

import app.skymek.commons.testsupport.SupportGrants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportGrantTest {

    private static final Instant NOW = Instant.parse("2026-10-31T20:00:00Z");

    @ParameterizedTest(name = "{0} after {1}s: usable={2}")
    @CsvSource({
            "admin-sub,   0,    true",
            "other-admin, 0,    false",
            "admin-sub,   1801, false"
    })
    void sessionIsUsableOnlyByItsAdminBeforeExpiry(String sub, long secondsLater, boolean usable) {
        SupportGrant session = SupportGrants.taller(42).expiringAt(NOW.plusSeconds(1800)).build();

        assertEquals(usable, session.isUsableBy(sub, NOW.plusSeconds(secondsLater)));
    }
}
