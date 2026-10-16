package com.example.vulnapp.web;

import java.io.IOException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RedirectController {

    // Intentional: Open Redirect
    @GetMapping("/redirect")
        @Operation(summary = "Redirect to a URL - open redirect",
            description = "Intentionally redirects to an unvalidated target with HTTP 302. "
                + "The relative default stays on this server so Swagger can follow it. External destinations "
                + "may show Failed to fetch because the browser enforces CORS after the redirect; "
                + "use normal browser navigation or a client that does not follow redirects to inspect the 302.")
        @Parameter(name = "target", in = ParameterIn.QUERY,
            description = "Relative or absolute destination URL placed in the redirect Location header.",
            schema = @Schema(type = "string", defaultValue = "greet?name=Alice"))
    public void redirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getParameter("target"));
    }
}
