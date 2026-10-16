package com.example.vulnapp.web;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final JdbcTemplate jdbcTemplate;

    public AccountController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Intentional: the requested account is not checked against the authenticated principal.
    @GetMapping("/accounts/{username}")
        @Operation(summary = "Read an account - IDOR",
            description = "Intentionally checks only that a Principal exists, not that it owns the requested account. "
                + "An authenticated alice can request bob's email and balance. First call /login, then paste its "
                + "plain-text token into Swagger's Authorize dialog. API clients send `Authorization: Bearer TOKEN`. "
                + "Missing, invalid, or expired tokens return 401. Tokens expire after one hour or an app restart.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> getAccount(
            @Parameter(description = "Account owner to look up; seeded owners are alice, bob, and carol.",
                schema = @Schema(defaultValue = "bob")) @PathVariable String username, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
                "SELECT u.username AS \"username\", u.email AS \"email\", a.balance AS \"balance\" "
                        + "FROM users u JOIN accounts a ON a.username = u.username "
                        + "WHERE u.username = ?",
                username);
        if (accounts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(accounts.get(0));
    }
}
