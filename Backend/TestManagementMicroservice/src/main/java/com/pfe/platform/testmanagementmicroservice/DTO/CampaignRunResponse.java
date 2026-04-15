package com.pfe.platform.testmanagementmicroservice.DTO;

import java.util.List;

public record CampaignRunResponse(String status,
                                  String message,
                                  String sessionId,
                                  TestExecutionDto execution,
                                  List<EndpointDto> endpoints,
                                  Boolean missingDb,
                                  List<String> missingEnvVars,
                                  List<String> dbOptions,
                                  List<String> notes,
                                  List<EditableFileDto> editableFiles) {
}
