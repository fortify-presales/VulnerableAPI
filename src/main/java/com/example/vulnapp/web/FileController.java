package com.example.vulnapp.web;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {

    private final String baseDir;

    public FileController(@Value("${app.files.base-dir}") String baseDir) {
        this.baseDir = baseDir;
    }

    @PostConstruct
    public void initializeSampleFile() throws IOException {
        Path directory = Path.of(baseDir);
        Files.createDirectories(directory);
        try {
            Files.writeString(directory.resolve("sample.txt"), "Hello from the files endpoint!\n",
                    StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
        }
    }

    // Intentional: Path Traversal
    @GetMapping("/files")
        @Operation(summary = "Download a file - path traversal",
            description = "Intentionally combines the supplied name with app.files.base-dir without checking "
                + "that the result stays inside that directory. Reads files from the server, not your browser. "
                + "Startup creates sample.txt without overwriting existing content. Missing or unreadable files "
                + "currently produce 500 because file-read exceptions are not handled.")
        public byte[] download(@Parameter(description = "File path relative to app.files.base-dir "
                + "(default /tmp/vulnapp-files); sample.txt is created at startup.",
                schema = @Schema(defaultValue = "sample.txt"))
            @RequestParam String name) throws IOException {
        File file = new File(baseDir, name);
        return Files.readAllBytes(file.toPath());
    }
}
