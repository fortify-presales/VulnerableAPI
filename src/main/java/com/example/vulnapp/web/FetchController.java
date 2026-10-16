package com.example.vulnapp.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FetchController {

    // Intentional: Server-Side Request Forgery
    @SuppressWarnings("deprecation")
    @GetMapping("/fetch")
        @Operation(summary = "Fetch a URL - server-side request forgery",
            description = "Intentionally opens a user-supplied URL from the server without restricting destinations "
                + "or protocols, demonstrating SSRF. The default fetches example.com and requires outbound HTTPS "
                + "and DNS access from the server. Only request destinations you are authorized to test.")
        public String fetch(
            @Parameter(description = "Absolute URL fetched by the server; its response is returned as text.",
                schema = @Schema(defaultValue = "https://example.com")) @RequestParam String url) throws IOException {
        try (InputStream in = new URL(url).openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
