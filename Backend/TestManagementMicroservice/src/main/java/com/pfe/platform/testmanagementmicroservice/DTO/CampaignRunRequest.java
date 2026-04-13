package com.pfe.platform.testmanagementmicroservice.DTO;

import java.util.Map;

public record CampaignRunRequest(String branch,
                                 String db,
                                 Map<String, String> envValues,
                                 Integer hostPortBase,
                                 Boolean useOllama,
                                 String ollamaModel) {
}
