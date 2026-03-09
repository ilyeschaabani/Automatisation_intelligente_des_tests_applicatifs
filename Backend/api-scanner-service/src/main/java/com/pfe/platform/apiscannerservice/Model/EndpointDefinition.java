package com.pfe.platform.apiscannerservice.Model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointDefinition {
    private String httpMethod;
    private String path;
    private String controller;
    private String handler;

    @Builder.Default
    private List<String> produces = new ArrayList<>();

    @Builder.Default
    private List<String> consumes = new ArrayList<>();

    public EndpointDefinition(String httpMethod, String path) {
        this.httpMethod = httpMethod;
        this.path = path;
    }
}
