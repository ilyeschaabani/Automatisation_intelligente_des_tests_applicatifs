package com.pfe.platform.testmanagementmicroservice.DTO;

import java.util.List;

public record DiscoveryFlowDto(
        Long discoveryId,
        String status,
        String jobId,
        Object questionnaire,
        List<EndpointDto> endpoints,
        String error
) {
}
