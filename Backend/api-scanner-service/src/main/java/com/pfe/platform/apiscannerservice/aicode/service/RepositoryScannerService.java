package com.pfe.platform.apiscannerservice.aicode.service;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.model.IngestedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RepositoryScannerService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryScannerService.class);

    private static final Set<String> ALLOWED_EXT = Set.of(
            ".java", ".kt", ".js", ".ts", ".py", ".php", ".yml", ".yaml", ".json"
    );

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", ".git", "target", "build", "dist", ".next", ".nuxt", "out"
    );

    private final AiCodeAnalyzerProperties props;

    public RepositoryScannerService(AiCodeAnalyzerProperties props) {
        this.props = props;
    }

    public List<IngestedFile> ingest(Path repoRoot) {
        if (repoRoot == null) throw new IllegalArgumentException("repoRoot must not be null");
        if (!Files.isDirectory(repoRoot)) throw new IllegalArgumentException("repoRoot must be a directory: " + repoRoot);

        List<IngestedFile> files = new ArrayList<>();
        Set<Path> visited = new HashSet<>();
        try {
            log.info("aicode.ingest.start repoRoot={}", repoRoot);
            Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path name = dir.getFileName();
                    if (name != null && EXCLUDED_DIRS.contains(name.toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (files.size() >= props.getMaxFiles()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;

                    String fn = file.getFileName().toString().toLowerCase();
                    String ext = extensionOf(fn);
                    if (!ALLOWED_EXT.contains(ext)) return FileVisitResult.CONTINUE;

                    Path norm = file.toAbsolutePath().normalize();
                    if (!visited.add(norm)) return FileVisitResult.CONTINUE;

                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    files.add(new IngestedFile(repoRoot.relativize(file), content));
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("aicode.ingest.done files={}", files.size());
            return files;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to ingest repository: " + repoRoot, e);
        }
    }

    private static String extensionOf(String fileNameLower) {
        int idx = fileNameLower.lastIndexOf('.');
        return idx >= 0 ? fileNameLower.substring(idx) : "";
    }
}

