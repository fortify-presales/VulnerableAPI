package com.example.vulnapp;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.media.XML;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
    title = "VulnerableAPI Demo API",
    version = "1.0.0-SNAPSHOT",
    description = "An intentionally vulnerable Spring Boot API for Fortify security scanning, "
        + "security training, and controlled API testing. It includes user and account lookups, "
        + "demo login and encryption, fund transfers, file access, URL fetching, redirects, "
        + "command execution, XML parsing, and Java object deserialization.\n\n"
        + "**Warning: This application is intentionally insecure. Never expose it to public or "
        + "untrusted networks or use real credentials, sensitive data, or untrusted inputs.** "
        + "Requests execute real operations and may change demo balances, read server files, "
        + "make outbound requests, or run server-side commands.\n\n"
        + "Each operation describes its intentional vulnerability, parameters, test defaults, "
        + "and environment requirements. To test accounts, call /login with alice/password, copy the plain-text "
        + "token into Authorize, and request /accounts/bob. Tokens expire after one hour or an app restart. "
        + "The other endpoints remain public. Authentication intentionally does not prevent cross-account access."))
    @SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer",
        description = "Paste the plain-text token returned by POST /login, without the Bearer prefix. "
            + "Demo tokens expire after one hour or an app restart and use intentionally weak randomness.")
public class VulnerableAPI {

    @Bean
    public OpenApiCustomizer xmlRequestExample() {
        return openApi -> openApi.getPaths().get("/xml").getPost().getRequestBody()
                .getContent().get("application/xml").getSchema().xml(new XML().name("message"));
    }

    public static void main(String[] args) {
        SpringApplication.run(VulnerableAPI.class, args);
    }
}
