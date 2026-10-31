package ai.aura.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aura-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "aura.auth.username=test-owner",
        "aura.auth.password=this-is-a-test-password-with-enough-length"
})
class ResearchJobApiTest {

    private static final String USERNAME = "test-owner";
    private static final String PASSWORD = "this-is-a-test-password-with-enough-length";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void researchJobsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/research-jobs/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsAndReadsAResearchJob() throws Exception {
        String location = mockMvc.perform(post("/api/v1/research-jobs")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"A public research topic","mode":"GENERAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/api/v1/research-jobs/")))
                .andExpect(jsonPath("$.subject").value("A public research topic"))
                .andExpect(jsonPath("$.mode").value("GENERAL"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("A public research topic"));
    }

    @Test
    void rejectsBlankSubject() throws Exception {
        mockMvc.perform(post("/api/v1/research-jobs")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"   ","mode":"GENERAL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
