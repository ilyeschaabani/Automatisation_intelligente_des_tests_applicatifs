package com.pfe.platform.apiscannerservice.Scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.pfe.platform.apiscannerservice.Model.ApiContract;
import com.pfe.platform.apiscannerservice.Model.EndpointDefinition;
import com.pfe.platform.apiscannerservice.Model.ProjectMetadata;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Component
public class SpringScanner implements FrameworkScanner {

    private static final Set<String> CONTROLLER_ANN = Set.of("RestController", "Controller");
    private static final Map<String, String> METHOD_ANN_TO_HTTP = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping", "PATCH"
    );

    @Override
    public boolean supports(ProjectMetadata metadata) {
        return metadata != null && metadata.getFramework() == ProjectMetadata.Framework.SPRING_BOOT;
    }

    @Override
    public ApiContract scan(Path projectPath) {
        List<EndpointDefinition> endpoints = new ArrayList<>();

        Path src = projectPath.resolve("src").resolve("main").resolve("java");
        if (!Files.exists(src)) {
            return new ApiContract(null, null, endpoints);
        }

        try (Stream<Path> paths = Files.walk(src)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> parseFile(p, endpoints));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan Spring project", e);
        }

        return new ApiContract(null, null, endpoints);
    }

    private void parseFile(Path javaFile, List<EndpointDefinition> endpoints) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                if (!isController(clazz)) return;

                String basePath = extractRequestMappingPath(clazz);
                String controllerName = clazz.getNameAsString();

                for (MethodDeclaration method : clazz.getMethods()) {
                    extractMethodEndpoints(method, basePath, controllerName).forEach(endpoints::add);
                }
            });

        } catch (Exception ignored) {
            // resilient: skip files that can't be parsed
        }
    }

    private boolean isController(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotations().stream()
                .map(a -> a.getName().getIdentifier())
                .anyMatch(CONTROLLER_ANN::contains);
    }

    private String extractRequestMappingPath(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotations().stream()
                .filter(a -> "RequestMapping".equals(a.getName().getIdentifier()))
                .findFirst()
                .map(this::extractFirstPathValue)
                .orElse("");
    }

    private List<EndpointDefinition> extractMethodEndpoints(MethodDeclaration method, String basePath, String controllerName) {
        List<EndpointDefinition> out = new ArrayList<>();

        method.getAnnotations().forEach(a -> {
            String ann = a.getName().getIdentifier();

            if (METHOD_ANN_TO_HTTP.containsKey(ann)) {
                String http = METHOD_ANN_TO_HTTP.get(ann);
                String path = joinPaths(basePath, extractFirstPathValue(a));
                EndpointDefinition ed = new EndpointDefinition(http, path);
                ed.setController(controllerName);
                ed.setHandler(method.getNameAsString());
                out.add(ed);
                return;
            }

            if ("RequestMapping".equals(ann)) {
                String path = joinPaths(basePath, extractFirstPathValue(a));
                List<String> methods = extractRequestMappingMethods(a);
                if (methods.isEmpty()) methods = List.of("ANY");

                for (String http : methods) {
                    EndpointDefinition ed = new EndpointDefinition(http, path);
                    ed.setController(controllerName);
                    ed.setHandler(method.getNameAsString());
                    out.add(ed);
                }
            }
        });

        return out;
    }

    private String extractFirstPathValue(com.github.javaparser.ast.expr.AnnotationExpr ann) {
        if (ann.isSingleMemberAnnotationExpr()) {
            SingleMemberAnnotationExpr s = ann.asSingleMemberAnnotationExpr();
            if (s.getMemberValue().isStringLiteralExpr()) {
                return s.getMemberValue().asStringLiteralExpr().asString();
            }
            return "";
        }

        if (ann.isNormalAnnotationExpr()) {
            NormalAnnotationExpr n = ann.asNormalAnnotationExpr();
            Optional<MemberValuePair> pair = n.getPairs().stream()
                    .filter(p -> "value".equals(p.getNameAsString()) || "path".equals(p.getNameAsString()))
                    .findFirst();

            if (pair.isPresent() && pair.get().getValue().isStringLiteralExpr()) {
                StringLiteralExpr s = pair.get().getValue().asStringLiteralExpr();
                return s.asString();
            }
        }

        return "";
    }

    private List<String> extractRequestMappingMethods(com.github.javaparser.ast.expr.AnnotationExpr ann) {
        if (!ann.isNormalAnnotationExpr()) return List.of();

        NormalAnnotationExpr n = ann.asNormalAnnotationExpr();
        Optional<MemberValuePair> pair = n.getPairs().stream()
                .filter(p -> "method".equals(p.getNameAsString()))
                .findFirst();

        if (pair.isEmpty()) return List.of();

        String raw = pair.get().getValue().toString();
        List<String> methods = new ArrayList<>();

        if (raw.contains("RequestMethod.GET")) methods.add("GET");
        if (raw.contains("RequestMethod.POST")) methods.add("POST");
        if (raw.contains("RequestMethod.PUT")) methods.add("PUT");
        if (raw.contains("RequestMethod.DELETE")) methods.add("DELETE");
        if (raw.contains("RequestMethod.PATCH")) methods.add("PATCH");

        return methods;
    }

    private String joinPaths(String base, String sub) {
        String a = base == null ? "" : base.trim();
        String b = sub == null ? "" : sub.trim();

        if (a.isEmpty() && b.isEmpty()) return "/";
        if (a.isEmpty()) return normalize(b);
        if (b.isEmpty()) return normalize(a);

        return normalize(a) + normalize("/" + b);
    }

    private String normalize(String p) {
        String s = p.replace("\\", "/").trim();
        if (s.isEmpty()) return "/";
        if (!s.startsWith("/")) s = "/" + s;
        while (s.contains("//")) s = s.replace("//", "/");
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
