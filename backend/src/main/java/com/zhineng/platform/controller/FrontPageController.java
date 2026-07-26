package com.zhineng.platform.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FrontPageController {

    private final Path frontRoot = locateFrontRoot();

    @GetMapping({"/", "/index.html"})
    public ResponseEntity<ByteArrayResource> index() throws IOException {
        return serve(frontRoot.resolve("index.html"), MediaType.TEXT_HTML);
    }

    @GetMapping("/assets/{fileName:.+}")
    public ResponseEntity<ByteArrayResource> asset(@PathVariable String fileName) throws IOException {
        Path asset = frontRoot.resolve("assets").resolve(fileName).normalize();
        if (!asset.startsWith(frontRoot.resolve("assets").normalize())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return serve(asset, mediaType(asset));
    }

    @GetMapping("/echarts.min.js")
    public ResponseEntity<ByteArrayResource> echarts() throws IOException {
        return serve(frontRoot.resolve("echarts.min.js"), MediaType.valueOf("application/javascript"));
    }

    private ResponseEntity<ByteArrayResource> serve(Path file, MediaType mediaType) throws IOException {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "前端资源不存在: " + file);
        }
        byte[] bytes = Files.readAllBytes(file);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .body(new ByteArrayResource(bytes));
    }

    private MediaType mediaType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".js")) {
            return MediaType.valueOf("application/javascript");
        }
        if (name.endsWith(".css")) {
            return MediaType.valueOf("text/css");
        }
        if (name.endsWith(".html")) {
            return MediaType.TEXT_HTML;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private Path locateFrontRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path[] candidates = new Path[] {
                cwd.resolve("frontend/dist"),
                cwd.resolve("../frontend/dist"),
                cwd.resolve("../../frontend/dist"),
                cwd.resolve("front"),
                cwd.resolve("../front"),
                cwd.resolve("../../front")
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate.resolve("index.html"))) {
                return candidate.normalize();
            }
        }
        return candidates[0].normalize();
    }
}
