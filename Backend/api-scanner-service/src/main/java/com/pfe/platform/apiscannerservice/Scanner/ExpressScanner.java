package com.pfe.platform.apiscannerservice.Scanner;

import com.pfe.platform.apiscannerservice.Model.ApiContract;
import com.pfe.platform.apiscannerservice.Model.EndpointDefinition;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Component
public class ExpressScanner implements FrameworkScanner {

    private static final List<String> TARGETS = List.of("app.", "router.");
    private static final Map<String, String> METHODS = Map.of(
            "get", "GET",
            "post", "POST",
            "put", "PUT",
            "delete", "DELETE",
            "patch", "PATCH"
    );

    @Override
    public boolean supports(ProjectMetadata metadata) {
        return metadata != null && metadata.getFramework() == ProjectMetadata.Framework.EXPRESS;
    }

    @Override
    public ApiContract scan(Path projectPath) {
        List<EndpointDefinition> endpoints = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().toLowerCase(Locale.ROOT);
                        return s.endsWith(".js") || s.endsWith(".ts");
                    })
                    .forEach(p -> scanFile(p, endpoints));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan Express project", e);
        }

        return new ApiContract(null, null, endpoints);
    }

    private void scanFile(Path file, List<EndpointDefinition> endpoints) {
        String content = readSmallFile(file);
        if (content.isEmpty()) return;

        for (String target : TARGETS) {
            for (Map.Entry<String, String> e : METHODS.entrySet()) {
                String needle = target + e.getKey() + "(";
                int idx = 0;
                while (true) {
                    idx = content.indexOf(needle, idx);
                    if (idx < 0) break;

                    int start = idx + needle.length();
                    String path = tryReadFirstStringArg(content, start);
                    if (path != null) {
                        EndpointDefinition ed = new EndpointDefinition(e.getValue(), normalize(path));
                        ed.setController(file.getFileName().toString());
                        endpoints.add(ed);
                    }
                    idx = start;
                }
            }
        }
    }

    private String tryReadFirstStringArg(String s, int pos) {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        if (pos >= s.length()) return null;

        char q = s.charAt(pos);
        if (q != '\'' && q != '"' && q != '`') return null;

        StringBuilder out = new StringBuilder();
        pos++;

        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '\\') {
                if (pos + 1 < s.length()) {
                    out.append(s.charAt(pos + 1));
                    pos += 2;
                    continue;
                }
            }
            if (c == q) return out.toString();
            out.append(c);
            pos++;
        }
        return null;
    }

    private String readSmallFile(Path path) {
        try {
            if (Files.size(path) > 2_000_000) return "";
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String normalize(String p) {
        String s = p.trim();
        if (s.isEmpty()) return "/";
        if (!s.startsWith("/")) s = "/" + s;
        while (s.contains("//")) s = s.replace("//", "/");
        return s;
    }
}
