package com.speeddesk.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "speeddesk.rate-limit.enabled=true",
        "speeddesk.rate-limit.public-requests-per-minute=2",
        "speeddesk.rate-limit.authenticated-requests-per-minute=2"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void rejectsRequestsAfterConfiguredPublicLimit() throws Exception {
        String body = "{\"email\":\"nobody@speeddesk.test\",\"password\":\"invalid-password\"}";

        for (int index = 0; index < 2; index++) {
            mockMvc.perform(post("/api/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(jsonPath("$.title").value("Limite de requisições excedido"));
    }
}
