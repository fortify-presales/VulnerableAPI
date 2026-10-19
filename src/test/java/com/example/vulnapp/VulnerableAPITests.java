package com.example.vulnapp;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.example.vulnapp.auth.DemoTokenService;
import com.example.vulnapp.web.FileController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VulnerableAPITests {

    @TempDir
    static Path filesDirectory;

    @DynamicPropertySource
    static void configureFilesDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.files.base-dir", () -> filesDirectory.resolve("downloads").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

        @Test
        void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"));
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        }

        @Test
        void openApiDocumentsEveryEndpointAndServletInputs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("VulnerableAPI Demo API"))
            .andExpect(jsonPath("$.info.version").value("1.0.0-SNAPSHOT"))
            .andExpect(jsonPath("$.info.description").isNotEmpty())
            .andExpect(jsonPath("$.paths.length()").value(13))
            .andExpect(jsonPath("$.paths['/accounts/{username}'].get").exists())
            .andExpect(jsonPath("$.paths['/login'].post").exists())
            .andExpect(jsonPath("$.paths['/encrypt'].post").exists())
            .andExpect(jsonPath("$.paths['/ping'].get").exists())
            .andExpect(jsonPath("$.paths['/deserialize'].post.requestBody.content['application/octet-stream'].schema.format").value("binary"))
            .andExpect(jsonPath("$.paths['/fetch'].get").exists())
            .andExpect(jsonPath("$.paths['/files'].get").exists())
            .andExpect(jsonPath("$.paths['/files'].get.parameters[0].schema.default").value("sample.txt"))
            .andExpect(jsonPath("$.paths['/greet'].get.parameters[0].name").value("name"))
            .andExpect(jsonPath("$.paths['/redirect'].get.parameters[0].name").value("target"))
            .andExpect(jsonPath("$.paths['/transfer'].post").exists())
            .andExpect(jsonPath("$.paths['/users'].get").exists())
            .andExpect(jsonPath("$.paths['/users/by-email'].get").exists())
            .andExpect(jsonPath("$.paths['/xml'].post.requestBody.content['application/xml']").exists());
        }

    @Test
    void everyOperationDescribesItsVulnerabilityAndInputsHaveDefaults() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(document).path("paths");
        for (JsonNode path : paths) {
            for (JsonNode operation : path) {
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("description").asText()).isNotBlank();
                for (JsonNode parameter : operation.path("parameters")) {
                    assertThat(parameter.path("description").asText()).isNotBlank();
                    assertThat(parameter.path("schema").has("default")).isTrue();
                }
                if (operation.has("requestBody")) {
                    assertThat(operation.path("requestBody").path("description").asText()).isNotBlank();
                }
            }
        }
        assertThat(paths.path("/redirect").path("get").path("parameters").get(0)
                .path("schema").path("default").asText()).isEqualTo("greet?name=Alice");
        JsonNode xmlSchema = paths.path("/xml").path("post").path("requestBody").path("content")
            .path("application/xml").path("schema");
        assertThat(xmlSchema.path("xml").path("name").asText()).isEqualTo("message");
        assertThat(xmlSchema.path("example").asText()).isEqualTo("Hello from Swagger");
        String xmlExample = "<message>" + xmlSchema.path("example").asText() + "</message>";
        mockMvc.perform(post("/xml").contentType("application/xml").content(xmlExample))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello from Swagger"));
    }

    @Test
    void startupSampleCanBeDownloaded() throws Exception {
        mockMvc.perform(get("/files").param("name", "sample.txt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("Hello from the files endpoint!\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }


    @Test
    void emailLookupReturnsTheMatchingUser() throws Exception {
        mockMvc.perform(get("/users/by-email").param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].USERNAME").value("alice"))
                .andExpect(jsonPath("$[0].EMAIL").value("alice@example.com"))
                .andExpect(jsonPath("$[0].PASSWORD_HASH").doesNotExist());
        mockMvc.perform(get("/users/by-email").param("email", "unknown@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void emailLookupIntentionallyAllowsSqlInjection() throws Exception {
        mockMvc.perform(get("/users/by-email").param("email", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].USERNAME",
                        org.hamcrest.Matchers.containsInAnyOrder("alice", "bob", "carol")));
    }

    @Test
    void sampleInitializationPreservesExistingFiles(@TempDir Path directory) throws Exception {
        Path sample = directory.resolve("sample.txt");
        Files.writeString(sample, "Existing content");

        new FileController(directory.toString()).initializeSampleFile();

        assertThat(Files.readString(sample)).isEqualTo("Existing content");
    }

    @Test
    void transferMovesFundsBetweenAccounts() throws Exception {
        resetBalances();

        mockMvc.perform(post("/transfer")
                        .param("from", "alice")
                        .param("to", "bob")
                        .param("amount", "100"))
                .andExpect(status().isOk());

        assertBalance("alice", "900.00");
        assertBalance("bob", "600.00");
    }

    @Test
    void negativeTransferAmountReversesTheTransfer() throws Exception {
        resetBalances();

        mockMvc.perform(post("/transfer")
                        .param("from", "alice")
                        .param("to", "bob")
                        .param("amount", "-100"))
                .andExpect(status().isOk());

        assertBalance("alice", "1100.00");
        assertBalance("bob", "400.00");
    }

    @Test
    void authenticatedUserCanReadAnotherUsersAccount() throws Exception {
        mockMvc.perform(get("/accounts/bob").principal(() -> "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.balance").value(500.0));
    }

    @Test
    void accountLookupRequiresAnAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(get("/accounts/bob"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginTokenAllowsCrossAccountAccess() throws Exception {
        String token = mockMvc.perform(post("/login").param("username", "alice").param("password", "password"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/accounts/bob").servletPath("/accounts/bob")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void tokensIdentifyUsersExpireAfterOneHourAndAreInstanceLocal() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        AtomicReference<Instant> currentTime = new AtomicReference<>(issuedAt);
        DemoTokenService service = new DemoTokenService(currentTime::get);
        String aliceToken = service.issueToken("alice");
        String bobToken = service.issueToken("bob");

        assertThat(aliceToken).isNotEqualTo(bobToken);
        assertThat(service.findUsername(aliceToken)).isEqualTo("alice");
        assertThat(service.findUsername(bobToken)).isEqualTo("bob");
        assertThat(service.findUsername("unknown-token")).isNull();
        assertThat(new DemoTokenService(currentTime::get).findUsername(aliceToken)).isNull();

        currentTime.set(issuedAt.plusSeconds(3599));
        assertThat(service.findUsername(aliceToken)).isEqualTo("alice");
        currentTime.set(issuedAt.plusSeconds(3600));
        assertThat(service.findUsername(aliceToken)).isNull();
        assertThat(service.findUsername(bobToken)).isNull();
    }

    @Test
    void accountLookupRejectsInvalidBearerTokens() throws Exception {
        for (String authorization : new String[] {"Bearer unknown-token", "Bearer ", "Basic invalid"}) {
            mockMvc.perform(get("/accounts/bob").servletPath("/accounts/bob")
                            .header("Authorization", authorization))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void invalidLoginDoesNotIssueAToken() throws Exception {
        mockMvc.perform(post("/login").param("username", "alice").param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid credentials"));
    }

    @Test
    void swaggerRequiresBearerAuthenticationOnlyForAccounts() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode openApi = objectMapper.readTree(document);
        assertThat(openApi.path("components").path("securitySchemes").path("bearerAuth")
                .path("scheme").asText()).isEqualTo("bearer");
        assertThat(openApi.has("security")).isFalse();
        for (JsonNode path : openApi.path("paths")) {
            for (JsonNode operation : path) {
                if (operation.path("operationId").asText().equals("getAccount")) {
                    assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
                } else {
                    assertThat(operation.has("security")).isFalse();
                }
            }
        }
    }

    private void resetBalances() {
        jdbcTemplate.update("UPDATE accounts SET balance = 1000.00 WHERE username = 'alice'");
        jdbcTemplate.update("UPDATE accounts SET balance = 500.00 WHERE username = 'bob'");
        jdbcTemplate.update("UPDATE accounts SET balance = 100.00 WHERE username = 'carol'");
    }

    private void assertBalance(String username, String expected) {
        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE username = ?", BigDecimal.class, username);
        assertThat(balance).isEqualByComparingTo(expected);
    }
}
