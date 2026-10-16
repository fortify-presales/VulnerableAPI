package com.example.vulnapp.web;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final JdbcTemplate jdbcTemplate;

    public UserController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Intentional: SQL Injection
    @GetMapping("/users")
        @Operation(summary = "Find users - SQL injection",
            description = "Intentionally concatenates the supplied name into SQL without parameterization. "
                + "The default looks up the seeded alice user; modified input can change the query.")
        public List<Map<String, Object>> findUsers(
            @Parameter(description = "Username to look up in the seeded users table.",
                schema = @Schema(defaultValue = "alice")) @RequestParam String name) {
        String sql = "SELECT id, username, email FROM users WHERE username = '" + name + "'";
        return jdbcTemplate.queryForList(sql);
    }
}
