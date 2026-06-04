package com.pfe.platform.ms_gestion.dto.request;

import lombok.Data;

@Data
public class GenerateTestRequest {
    private String type;
    private String description;
    private String databaseType;
    private String targetClassName;
    private Long suiteId;
    private String testData;         // JSON string with test input data
    private String skeleton;         // pre-extracted skeleton from frontend (avoids backend clone)

    // Structured generation fields (improve LLM accuracy)
    private String methodName;       // specific method to test (e.g. "add")
    private String scenarioType;     // HAPPY_PATH | EXCEPTION | NULL_INPUT | BOUNDARY | WRONG_INPUT
    private String expectedBehavior; // one-line description of the expected result
}
