package app.skymek.commons.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportModeRulesTest {

    @ParameterizedTest(name = "readOnly={0} {1} {2} -> {3}")
    @CsvSource({
            "true,  GET,    /api/workorder/talleres/42/cash-register/current,     true",
            "true,  POST,   /api/workorder/service-orders,                        false",
            "true,  POST,   /subscription/usage/track/vehicle,                    false",
            "false, PUT,    /talleres/42/schedules,                               true",
            "false, PUT,    /subscription/subscriptions/workshop/42/change-plan,  true",
            "false, POST,   /auth/users/123/reset-password,                       true",
            "false, DELETE, /talleres/42,                                         true"
    })
    void readModeBlocksEveryWriteAndWriteModeAllowsWhatTheClientCouldDo(boolean readOnly, String method, String path,
                                                                        boolean allowed) {
        assertEquals(allowed, SupportModeRules.allows(readOnly, method, path));
    }
}
