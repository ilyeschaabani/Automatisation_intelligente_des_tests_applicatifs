package com.pfe.platform.apiscannerservice.aicode.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.model.AnalyzeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LlmAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LlmAnalysisService.class);

    private final WebClient webClient;
    private final AiCodeAnalyzerProperties props;

    public LlmAnalysisService(AiCodeAnalyzerProperties props, WebClient.Builder builder) {
        this.props = props;
        this.webClient = builder.baseUrl(props.getOllamaBaseUrl()).build();
    }

    /**
     * Calls Ollama /api/chat and expects the model to respond with JSON that matches AnalyzeResponse.
     */
    public AnalyzeResponse analyze(String prompt) {
        String strictPrompt = "Return ONLY strict JSON (no markdown, no explanation, no code fences).\n\n" + prompt;

        ChatRequest req = new ChatRequest(
                props.getChatModel(),
                List.of(new Message("user", strictPrompt)),
                Map.of("temperature", 0)
        );

        ChatResponse res = webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(Duration.ofSeconds(120))
                .block();

        String raw = res != null && res.message != null ? res.message.content : null;
        if (raw == null || raw.isBlank()) {
            log.warn("aicode.llm.emptyResponse model={}", props.getChatModel());
            return emptyResult();
        }

        // Enable with: logging.level.com.pfe.platform.apiscannerservice.aicode.service.LlmAnalysisService=DEBUG
        log.debug("aicode.llm.rawResponse model={} rawResponse={}", props.getChatModel(), raw);

        String cleaned = cleanupToJson(raw);
        if (cleaned == null || cleaned.isBlank() || cleaned.charAt(0) != '{' || cleaned.charAt(cleaned.length() - 1) != '}') {
            log.warn("aicode.llm.invalidJsonEnvelope model={} cleaned={} rawResponse={}",
                    props.getChatModel(),
                    abbreviate(cleaned, 500),
                    abbreviate(raw, 6000));
            return emptyResult();
        }

        try {
            return JsonUtil.MAPPER.readValue(cleaned, AnalyzeResponse.class);
        } catch (Exception e) {
            // Log the raw response fully for debugging.
            log.warn("aicode.llm.parseFailed model={} err={} rawResponse={}", props.getChatModel(), e.getMessage(), raw);
            return emptyResult();
        }
    }

    private static AnalyzeResponse emptyResult() {
        return new AnalyzeResponse("UNKNOWN", AnalyzeResponse.Confidence.LOW, List.of());
    }

    /**
     * Strips ```json fences and extracts the first JSON object from the response.
     *
     * Also handles the edge case where the model returns a JSON string that itself contains a JSON object.
     */
    static String cleanupToJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();

        // Remove common markdown fences.
        s = s.replaceAll("(?is)^\\s*```\\s*json\\s*", "");
        s = s.replaceAll("(?is)^\\s*```\\s*", "");
        s = s.replaceAll("(?is)\\s*```\\s*$", "");
        s = s.trim();

        // If it's valid JSON already, keep it (or unwrap if it's a JSON string).
        try {
            JsonNode n = JsonUtil.MAPPER.readTree(s);
            if (n != null) {
                if (n.isTextual()) {
                    // Sometimes models respond with a quoted JSON object.
                    String inner = n.asText();
                    if (inner != null && inner.trim().startsWith("{") && inner.trim().endsWith("}")) {
                        return inner.trim();
                    }
                }
                if (n.isObject()) {
                    return s;
                }
            }
        } catch (Exception ignored) {
            // continue to extraction
        }

        // Extract the first top-level JSON object by brace matching.
        int start = s.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1).trim();
                }
            }
        }

        // Incomplete JSON: best-effort clip to start only (will fail envelope check upstream)
        return s.substring(start).trim();
    }

    static String abbreviate(String s, int max) {
        if (s == null) return null;
        if (max <= 0) return "";
        String t = s.replaceAll("\\r", "\\\\r").replaceAll("\\n", "\\\\n");
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    record ChatRequest(String model, List<Message> messages, Map<String, Object> options) {}

    record Message(String role, String content) {}

    static class ChatResponse {
        @JsonProperty("message")
        public ChatMessage message;
    }

    static class ChatMessage {
        @JsonProperty("content")
        public String content;
    }
}

