package com.example.vulnapp.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final JdbcTemplate jdbcTemplate;

    public TransferController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        List<Map<String, Object>> recipients = jdbcTemplate.queryForList(
                "SELECT username FROM accounts WHERE username = ?", to);
        if (recipients.isEmpty()) {
            return ResponseEntity.badRequest().body("Unknown recipient");
        }

        // Intentional: negative amounts pass the balance check and reverse the transfer.
        int debited = jdbcTemplate.update(
                "UPDATE accounts SET balance = balance - ? WHERE username = ? AND balance >= ?",
                amount, from, amount);
        if (debited == 0) {
            return ResponseEntity.badRequest().body("Insufficient funds or unknown sender");
        }

        jdbcTemplate.update("UPDATE accounts SET balance = balance + ? WHERE username = ?", amount, to);
        return ResponseEntity.ok("Transfer complete");
    }
}
