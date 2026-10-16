package com.example.vulnapp.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
        @Operation(summary = "Transfer funds - negative-amount business-logic flaw",
            description = "Intentionally omits positive-amount validation and sender authorization. "
                + "The default transfers 100 from alice to bob and changes real demo balances. "
                + "An amount of -100 reverses the balance updates, crediting the sender. "
                + "Repeated positive transfers may fail when the sender has insufficient funds.")
    public ResponseEntity<String> transfer(
            @Parameter(description = "Sender account username whose balance is debited.",
                schema = @Schema(defaultValue = "alice")) @RequestParam String from,
            @Parameter(description = "Recipient account username whose balance is credited.",
                schema = @Schema(defaultValue = "bob")) @RequestParam String to,
            @Parameter(description = "Decimal amount to transfer; negative values intentionally reverse the transfer.",
                schema = @Schema(defaultValue = "100")) @RequestParam BigDecimal amount) {
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
