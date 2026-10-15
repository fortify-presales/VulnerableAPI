package com.example.vulnapp;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VulnerableAppTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
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
