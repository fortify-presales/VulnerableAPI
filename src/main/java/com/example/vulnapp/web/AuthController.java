package com.example.vulnapp.web;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.vulnapp.util.CryptoUtil;
import com.example.vulnapp.auth.DemoTokenService;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JdbcTemplate jdbcTemplate;
    private final DemoTokenService tokenService;

    public AuthController(JdbcTemplate jdbcTemplate, DemoTokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
        @Operation(summary = "Demo login - log forging and weak credentials",
            description = "Intentionally logs the username without sanitizing line breaks, allowing forged log entries. "
                + "Also demonstrates MD5 password hashing, hard-coded admin credentials, and predictable Random-based "
                + "tokens. The defaults log in as seeded alice. Copy the plain-text response into Swagger's Authorize "
                + "dialog without the Bearer prefix. API clients send `Authorization: Bearer TOKEN` to /accounts. "
                + "Tokens expire after one hour and are lost on restart; no browser session cookie is created.")
        public ResponseEntity<String> login(
            @Parameter(description = "Username to authenticate; also written verbatim to the application log.",
                schema = @Schema(defaultValue = "alice")) @RequestParam String username,
            @Parameter(description = "Demo password for the selected user; alice's seeded password is password. "
                + "Sent as a query parameter, so do not use a real password.",
                schema = @Schema(defaultValue = "password")) @RequestParam String password) throws Exception {
        // Intentional: Log Forging
        log.info("Login attempt for user: " + username);

        if ("admin".equals(username) && CryptoUtil.ADMIN_PASSWORD.equals(password)) {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(tokenService.issueToken(username));
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE username = ? AND password_hash = ?",
                username, CryptoUtil.hashPassword(password));
        if (rows.isEmpty()) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(tokenService.issueToken(username));
    }

    @PostMapping("/encrypt")
        @Operation(summary = "Encrypt text - weak cryptography",
            description = "Intentionally encrypts using DES in ECB mode with a hard-coded key. "
                + "Returns Base64 ciphertext. This is a weak-cryptography demo, not suitable for real secrets.")
        public String encrypt(
            @Parameter(description = "Plaintext to encrypt using the intentionally weak demo cipher.",
                schema = @Schema(defaultValue = "Hello from Swagger")) @RequestParam String value) throws Exception {
        return CryptoUtil.encrypt(value);
    }
}
