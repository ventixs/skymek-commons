package app.skymek.commons.security;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenClaimsTest {

    @Mock
    JsonWebToken jwt;

    static Stream<Arguments> claimValues() {
        return Stream.of(
                arguments(7, 7L),
                arguments(7L, 7L),
                arguments("7", 7L),
                arguments("\"7\"", 7L),
                arguments(" 7 ", 7L),
                arguments("", null),
                arguments("taller-7", null),
                arguments(null, null));
    }

    @ParameterizedTest(name = "[{0}] -> {1}")
    @MethodSource("claimValues")
    void idsReadAsWholeNumbersWhateverTheMapperEmits(Object claim, Long expected) {
        assertEquals(expected, TokenClaims.id(claim));
    }

    @Nested
    class TallerId {

        @Test
        void workshopIdIsTheTaller() {
            when(jwt.getClaim("workshopId")).thenReturn(17L);
            assertEquals(17L, TokenClaims.tallerId(jwt));
        }

        @Test
        void legacyTallerIdIsReadWithoutWorkshopId() {
            when(jwt.getClaim("workshopId")).thenReturn(null);
            when(jwt.getClaim("tallerId")).thenReturn("12");
            assertEquals(12L, TokenClaims.tallerId(jwt));
        }

        @Test
        void missingTokenHasNoTaller() {
            assertNull(TokenClaims.tallerId(null));
        }
    }

    @Nested
    class Subject {

        @Test
        void blankSubjectIsNull() {
            when(jwt.getSubject()).thenReturn(" ");
            assertNull(TokenClaims.subject(jwt));
        }

        @Test
        void textDropsTheQuotesOfAJsonString() {
            when(jwt.getClaim("email")).thenReturn("\"ana@taller.pe\"");
            assertEquals("ana@taller.pe", TokenClaims.text(jwt, "email"));
        }
    }
}
