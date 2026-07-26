package com.zhineng.platform.evaluation.internal.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InternalEvaluationStorageService {
    private final Path root;

    public InternalEvaluationStorageService(
            @Value("${app.storage.internal-evaluation-path:"
                    + "backend/storage/internal-evaluation}") String configured
    ) throws IOException {
        Path path = Path.of(configured);
        if (!path.isAbsolute()) {
            Path cwd = Path.of("").toAbsolutePath().normalize();
            Path projectRoot = "backend".equalsIgnoreCase(String.valueOf(cwd.getFileName()))
                    ? cwd.getParent() : cwd;
            path = projectRoot.resolve(path);
        }
        root = path.toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredFile store(InputStream input, String extension) throws IOException {
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = resolve(storedName);
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredFile(storedName, storedName);
    }

    public Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法附件路径");
        }
        return resolved;
    }

    public Path resolveForRead(String relativePath) throws IOException {
        Path lexical = resolve(relativePath);
        Path canonicalRoot = root.toRealPath();
        Path canonicalFile = lexical.toRealPath();
        if (!canonicalFile.startsWith(canonicalRoot)) {
            throw new IllegalArgumentException("非法附件路径");
        }
        return canonicalFile;
    }

    public void deleteQuietly(String relativePath) {
        try { Files.deleteIfExists(resolve(relativePath)); }
        catch (IOException | IllegalArgumentException ignored) { }
    }

    public record StoredFile(String storedName, String relativePath) {
    }
}
