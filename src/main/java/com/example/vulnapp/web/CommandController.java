package com.example.vulnapp.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommandController {

    // Intentional: Command Injection
    @GetMapping("/ping")
        @Operation(summary = "Ping a host - command injection",
            description = "Intentionally inserts the host into a command executed by sh -c without escaping. "
                + "The default pings loopback once. Requires sh and ping on the server and permission to send ICMP; "
                + "minimal containers may not support this. Only use trusted inputs.")
        public String ping(
            @Parameter(description = "Hostname or IP address passed to the server's ping command.",
                schema = @Schema(defaultValue = "127.0.0.1")) @RequestParam String host) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[] {"sh", "-c", "ping -c 1 " + host});
        try (InputStream in = process.getInputStream()) {
            String output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output;
        }
    }
}
