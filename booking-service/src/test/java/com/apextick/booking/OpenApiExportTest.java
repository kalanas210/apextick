package com.apextick.booking;

import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exports the springdoc document the running app serves, so the WSO2 gateway
 * contract can be regenerated without a live stack:
 *
 * <pre>
 *   ./mvnw test -Dtest=OpenApiExportTest -Dsurefire.failIfNoSpecifiedTests=false
 *   python scripts/wso2/normalise-openapi.py \
 *       booking-service/target/openapi/api-docs.json wso2/apextick-api/openapi.json
 * </pre>
 *
 * An exporter, not a contract check: it asserts only that there is a document to
 * write, never what is in it.
 */
@IntegrationTest
class OpenApiExportTest {

    static final Path OUTPUT = Path.of("target", "openapi", "api-docs.json");

    @Autowired
    MockMvc mvc;

    @Test
    void exports_the_openapi_document() throws Exception {
        byte[] doc = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths").isNotEmpty())
                .andReturn().getResponse().getContentAsByteArray();

        Files.createDirectories(OUTPUT.getParent());
        Files.write(OUTPUT, doc);
    }
}
