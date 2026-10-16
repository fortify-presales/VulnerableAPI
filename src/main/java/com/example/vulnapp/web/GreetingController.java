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
public class GreetingController {

    // Intentional: Reflected Cross-Site Scripting
    @GetMapping("/greet")
        @Operation(summary = "Render a greeting - reflected cross-site scripting",
            description = "Intentionally writes the name directly into HTML without output encoding, "
                + "demonstrating reflected XSS. The default produces a harmless greeting. "
                + "Swagger displays response content; it does not reproduce all behavior of navigating to the HTML page.")
        @Parameter(name = "name", in = ParameterIn.QUERY,
            description = "Name inserted verbatim into the HTML greeting.",
            schema = @Schema(type = "string", defaultValue = "Alice"))
    public void greet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        response.setContentType("text/html");
        response.getWriter().write("<html><body><h1>Hello " + name + "</h1></body></html>");
    }
}
