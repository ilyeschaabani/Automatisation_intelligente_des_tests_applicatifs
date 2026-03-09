package com.pfe.platform.apiscannerservice.Model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiContract {
    private String source; // repoUrl or projectPath
    private ProjectMetadata metadata;

    @Builder.Default
    private Instant generatedAt = Instant.now();

    @Builder.Default
    private List<EndpointDefinition> endpoints = new ArrayList<>();

    /**
     * Non-fatal issues encountered during detection/scanning (e.g., unknown framework, partial parsing).
     */
    @Builder.Default
    private List<String> issues = new ArrayList<>();

    public ApiContract(String source, ProjectMetadata metadata, List<EndpointDefinition> endpoints) {
        this.source = source;
        this.metadata = metadata;
        this.endpoints = endpoints != null ? endpoints : new ArrayList<>();
    }
}
