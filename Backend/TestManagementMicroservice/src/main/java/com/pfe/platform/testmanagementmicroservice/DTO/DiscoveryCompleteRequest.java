package com.pfe.platform.testmanagementmicroservice.DTO;

import java.util.List;

public record DiscoveryCompleteRequest(
        List<Answer> answers,
        Boolean overwrite,
        Boolean return_openapi
) {
    public record Answer(
            String json_path,
            Object value
    ) {
    }
}
