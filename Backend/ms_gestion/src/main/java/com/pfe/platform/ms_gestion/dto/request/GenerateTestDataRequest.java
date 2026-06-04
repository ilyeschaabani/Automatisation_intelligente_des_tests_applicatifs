package com.pfe.platform.ms_gestion.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class GenerateTestDataRequest {
    private String methodName;     // méthode à tester
    private String scenarioType;   // HAPPY_PATH | EXCEPTION | NULL_INPUT | WRONG_INPUT | BOUNDARY
    private String targetClassName;
    private String skeleton;       // squelette pour comprendre le domaine
    private List<FieldSchema> fields; // champs depuis Swagger REQUEST schema

    @Data
    public static class FieldSchema {
        private String name;
        private String type;
        private List<String> enumValues;
        private boolean required;
        private Integer minimum;
        private Integer maximum;
        private Integer minLength;
        private Integer maxLength;
        private String format;
    }
}
