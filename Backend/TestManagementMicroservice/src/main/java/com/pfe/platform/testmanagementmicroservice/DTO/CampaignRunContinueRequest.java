package com.pfe.platform.testmanagementmicroservice.DTO;

import java.util.Map;

public record CampaignRunContinueRequest(String sessionId,
                                         String db,
                                         Map<String, String> envValues,
                                         Map<String, String> fileOverrides,
                                         Integer hostPortBase,
                                         Boolean useOllama,
                                         String ollamaModel) {
}
