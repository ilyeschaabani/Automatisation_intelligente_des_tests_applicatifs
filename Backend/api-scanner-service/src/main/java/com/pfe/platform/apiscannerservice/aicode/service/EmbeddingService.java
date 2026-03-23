package com.pfe.platform.apiscannerservice.aicode.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final WebClient webClient;

    public EmbeddingService(AiCodeAnalyzerProperties props, WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl(props.getOllamaBaseUrl())
                .build();
    }

    public float[] embed(String text) {
        if (text == null) text = "";

        EmbeddingsRequest req = new EmbeddingsRequest("nomic-embed-text", text);
        EmbeddingsResponse res = webClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(EmbeddingsResponse.class)
                .timeout(Duration.ofSeconds(60))
                .block();

        if (res == null || res.embedding == null) {
            throw new IllegalStateException("Ollama embeddings returned empty response");
        }
        return toFloatArray(res.embedding);
    }

    private static float[] toFloatArray(List<Double> doubles) {
        float[] out = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            out[i] = doubles.get(i) != null ? doubles.get(i).floatValue() : 0f;
        }
        return out;
    }

    record EmbeddingsRequest(String model, String prompt) {}

    static class EmbeddingsResponse {
        @JsonProperty("embedding")
        public List<Double> embedding;
    }
}

