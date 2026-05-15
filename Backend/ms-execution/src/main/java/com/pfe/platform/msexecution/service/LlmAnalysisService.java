package com.pfe.platform.msexecution.service;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class LlmAnalysisService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "deepseek-coder:6.7b";
    private static final int MAX_LOG_CHARS = 2000;

    private final RestTemplate restTemplate = new RestTemplate();

    public String analyzeFailure(String logs) {
        String truncatedLogs = truncate(logs);
        String prompt = buildPrompt(truncatedLogs);

        try {
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "prompt", prompt,
                    "stream", false
            );

            Map response = restTemplate.postForObject(OLLAMA_URL, body, Map.class);
            if (response == null || !response.containsKey("response")) {
                log.warn("Ollama did not return an analysis response.");
                return null;
            }

            Object raw = response.get("response");
            return raw != null ? raw.toString().trim() : null;
        } catch (RestClientException ex) {
            log.warn("Ollama analysis service is unavailable: {}", ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Unexpected error while analyzing failure logs: {}", ex.getMessage());
            return null;
        }
    }

    private String buildPrompt(String logs) {
        return "You are a senior test automation engineer. Analyze the following failing execution logs, identify the most likely root cause, and provide a concise remediation plan. Return only the analysis text.\n\nLogs:\n" + logs;
    }

    private String truncate(String logs) {
        if (logs == null || logs.isBlank()) {
            return "";
        }
        return logs.length() <= MAX_LOG_CHARS ? logs : logs.substring(0, MAX_LOG_CHARS);
    }
}