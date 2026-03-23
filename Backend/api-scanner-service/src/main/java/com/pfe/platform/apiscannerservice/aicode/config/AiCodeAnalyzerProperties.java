package com.pfe.platform.apiscannerservice.aicode.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "aicode")
public class AiCodeAnalyzerProperties {

    /** Base URL of Ollama server, e.g. http://localhost:11434 */
    @NotBlank
    private String ollamaBaseUrl = "http://localhost:11434";

    /** Ollama model used for chat/completions. Example: qwen2.5-coder:7b */
    @NotBlank
    private String chatModel = "qwen2.5-coder:7b";

    /** Max number of code/config files to ingest to avoid huge repos blowing up memory. */
    @Min(1)
    @Max(10_000)
    private int maxFiles = 400;

    /** Max characters per chunk when splitting files. */
    @Min(200)
    @Max(50_000)
    private int maxChunkChars = 2000;

    /** Max total prompt characters sent to the LLM. */
    @Min(1000)
    @Max(500_000)
    private int maxPromptChars = 24_000;

    /** Number of top chunks retrieved from the vector store for a query. */
    @Min(1)
    @Max(50)
    private int vectorTopK = 8;

    /** Root under which temp repositories are created. Defaults to java.io.tmpdir. */
    @NotBlank
    private String gitTempRoot = System.getProperty("java.io.tmpdir");
}
