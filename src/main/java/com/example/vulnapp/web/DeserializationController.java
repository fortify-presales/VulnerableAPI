package com.example.vulnapp.web;

import java.io.IOException;
import java.io.ObjectInputStream;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeserializationController {

    // Intentional: Insecure Deserialization of untrusted data
    @PostMapping("/deserialize")
    @Operation(summary = "Read a serialized object - insecure deserialization",
            description = "Intentionally deserializes untrusted bytes with ObjectInputStream without an object filter. "
                    + "Only upload a trusted test object. Returns Deserialized: java.lang.String for a serialized String. "
                    + "Ordinary text, XML, JSON, and ZIP files are not Java object streams and currently cause 500. "
                    + "Browsers cannot preselect a local file, so this input requires manual selection.")
    @RequestBody(required = true, description = "Binary file produced by Java ObjectOutputStream.writeObject(), "
                    + "sent as the raw body, not multipart form data. For a harmless sample, run in jshell: "
                    + "try (var out = new java.io.ObjectOutputStream(new java.io.FileOutputStream(\"sample.ser\"))) "
                    + "{ out.writeObject(\"Hello from Swagger\"); } Then select sample.ser below.",
            content = @Content(mediaType = "application/octet-stream",
                schema = @Schema(type = "string", format = "binary")))
    public String deserialize(HttpServletRequest request) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(request.getInputStream())) {
            Object obj = in.readObject();
            return "Deserialized: " + obj.getClass().getName();
        }
    }
}
