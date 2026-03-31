package com.pfe.platform.testmanagementmicroservice.DTO;

import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;

public record EndpointDto(
        Long id,
        String method,
        String path,
        String summary,
        String source,
        double confidence,
        String requestSchema
) {
    public static EndpointDto fromEntity(Endpoint e) {
        return new EndpointDto(
                e.getId(),
                e.getMethod(),
                e.getPath(),
                e.getSummary(),
                e.getSource(),
                e.getConfidence(),
                e.getRequestSchema()
        );
    }
}