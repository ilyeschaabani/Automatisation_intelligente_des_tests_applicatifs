package com.pfe.platform.apiscannerservice.aicode.service;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.model.AnalyzeResponse;
import com.pfe.platform.apiscannerservice.aicode.model.CodeChunk;
import com.pfe.platform.apiscannerservice.aicode.model.IngestedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

@Service
public class AiCodeAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(AiCodeAnalyzerService.class);
    private static final int PROMPT_LOG_MAX_CHARS = 4000;
    private static final String OLLAMA_API_URL = "http://127.0.0.1:11434/v1/completions";

    private final GitService gitService;
    private final RepositoryScannerService scannerService;
    private final CodeChunkerService chunker;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;
    private final AiCodeAnalyzerProperties props;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiCodeAnalyzerService(GitService gitService,
                                 RepositoryScannerService scannerService,
                                 CodeChunkerService chunker,
                                 EmbeddingService embeddingService,
                                 VectorStoreService vectorStore,
                                 AiCodeAnalyzerProperties props) {
        this.gitService = gitService;
        this.scannerService = scannerService;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.props = props;
    }

    public AnalyzeResponse analyzeRepo(String repoUrl, String gitToken) {
        Path repoDir = null;
        try {
            // Clone repo
            repoDir = gitService.cloneToTemp(repoUrl, gitToken);

            // Ingest files
            List<IngestedFile> files = scannerService.ingest(repoDir);
            List<CodeChunk> chunks = chunker.chunk(files);

            log.info("aicode.embed.start chunks={}", chunks.size());
            for (CodeChunk c : chunks) {
                float[] emb = embeddingService.embed(c.text());
                vectorStore.put(c, emb);
            }
            log.info("aicode.embed.done stored={}", chunks.size());

            // General question: detect framework automatically
            float[] queryEmbedding = embeddingService.embed(
                    "Identify the primary framework(s) used in this repository. "
                            + "It can be any programming language. Provide evidence from filenames, classes, or configuration."
            );
            List<VectorStoreService.StoredVector> top = vectorStore.topKSimilar(queryEmbedding, props.getVectorTopK());

            String prompt = buildPrompt(top);

            if (log.isDebugEnabled()) {
                log.debug("aicode.ollama.prompt chars={} sha256={} promptPrefix={}",
                        prompt.length(), sha256Hex(prompt), abbreviateForLog(prompt, PROMPT_LOG_MAX_CHARS));
            }

            // Call Ollama and parse
            String jsonResponse = callOllama(prompt);
            return parseAnalyzeResponse(jsonResponse);

        } finally {
            gitService.deleteRepoQuietly(repoDir);
        }
    }

    private String buildPrompt(List<VectorStoreService.StoredVector> top) {
        String system = "You are a codebase analyzer. Determine the primary framework(s) used by this repository. "
                + "It can be written in any programming language. "
                + "Return ONLY valid JSON with keys: framework, confidence, evidence. "
                + "Evidence should include filenames, class names, or configuration that support your answer. "
                + "Do NOT add markdown, explanations, or extra characters.";

        String schema = "Response JSON format:\n"
                + "{\n"
                + "  \"framework\": \"...\",\n"
                + "  \"confidence\": \"HIGH|MEDIUM|LOW\",\n"
                + "  \"evidence\": [\"...\"]\n"
                + "}";

        StringBuilder sb = new StringBuilder();
        sb.append(system).append("\n\n").append(schema).append("\n\n");
        sb.append("Relevant code chunks:\n");

        int budget = props.getMaxPromptChars();
        int used = sb.length();

        for (VectorStoreService.StoredVector sv : top) {
            CodeChunk c = sv.chunk();
            String header = "\n--- FILE: " + c.filePath() + " (chunk " + c.chunkIndex() + ") ---\n";
            String body = c.text();

            if (used + header.length() >= budget) break;
            sb.append(header);
            used += header.length();

            int remaining = budget - used;
            if (remaining <= 0) break;
            if (body.length() > remaining) {
                sb.append(body, 0, remaining);
                break;
            } else {
                sb.append(body);
                used += body.length();
            }
        }

        return sb.toString();
    }

    private String callOllama(String prompt) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", "qwen2.5-coder:7b");
            payload.put("prompt", prompt);
            payload.put("max_tokens", 512);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(OLLAMA_API_URL, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("Ollama API returned HTTP {}: {}", response.getStatusCode(), response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("Error calling Ollama: {}", e.getMessage(), e);
            return null;
        }
    }

    private AnalyzeResponse parseAnalyzeResponse(String jsonResponse) {
        if (jsonResponse == null) return null;

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String text = choices.get(0).path("text").asText();
                String cleaned = cleanOllamaResponse(text);
                return objectMapper.readValue(cleaned, AnalyzeResponse.class);
            } else {
                log.error("Unexpected Ollama response (no choices): {}", jsonResponse);
                return null;
            }
        } catch (Exception e) {
            log.error("Error parsing Ollama JSON: {} \nraw={}", e.getMessage(), jsonResponse);
            return null;
        }
    }

    private String cleanOllamaResponse(String text) {
        if (text == null) return null;
        return text.replaceAll("(?i)^```json\\s*", "")
                .replaceAll("```$", "")
                .trim();
    }

    private static String abbreviateForLog(String s, int maxChars) {
        if (s == null) return null;
        String t = s.replaceAll("\\r", "\\\\r").replaceAll("\\n", "\\\\n");
        if (t.length() <= maxChars) return t;
        return t.substring(0, maxChars) + "…";
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }
}