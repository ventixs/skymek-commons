package app.skymek.commons.error;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorResponseTest {

    @Test
    void bodyWithoutFieldErrorsCarriesAnEmptyList() {
        ErrorResponse body = ErrorResponse.of(409, "SUB_055", "Card held", "/api/data/subscription", null);

        assertEquals(List.of(), body.fieldErrors());
        assertNotNull(body.timestamp());
    }

    @Test
    void badRequestKeepsTheServiceCode() {
        ErrorResponse body = ErrorResponse.badRequest("LOCATION_001", "lat and lng are required", "/locations/nearby");

        assertEquals(400, body.status());
        assertEquals("LOCATION_001", body.errorCode());
    }

    @Test
    void fieldErrorsAreKeptInOrder() {
        List<ErrorResponse.FieldError> errors = List.of(new ErrorResponse.FieldError("email", "must not be blank", null),
                new ErrorResponse.FieldError("phone", "too long", null));

        assertEquals(errors, ErrorResponse.of(400, "Validation Error", "Invalid", "/x", errors).fieldErrors());
    }
}
