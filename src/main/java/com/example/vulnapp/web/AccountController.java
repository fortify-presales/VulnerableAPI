package com.example.vulnapp.web;

import java.security.Principal;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Map<String, Object>> getAccount(
            @PathVariable String username, Principal principal) {
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
