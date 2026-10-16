package com.example.vulnapp.web;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;

@RestController
public class XmlController {

    // Intentional: XML External Entity (XXE) injection - default parser settings
    @PostMapping(value = "/xml", consumes = "application/xml")
        @Operation(summary = "Parse XML - XML external entity injection",
            description = "Intentionally uses default XML parser settings without disabling external entities, "
                + "demonstrating XXE. Returns the root element's text content. The example is valid, harmless XML; "
                + "a declaration or comment without a root element is invalid. Malformed XML currently returns 500.")
        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            description = "Complete XML document with one root element; its text content is returned.",
                content = @Content(mediaType = "application/xml",
                    schema = @Schema(type = "string", example = "Hello from Swagger")))
    public String parse(@RequestBody String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement().getTextContent();
    }
}
