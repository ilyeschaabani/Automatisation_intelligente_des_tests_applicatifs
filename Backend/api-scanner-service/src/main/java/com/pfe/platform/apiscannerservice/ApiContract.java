package com.pfe.platform.apiscannerservice;

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
public class ApiContract {
    private String source;

    @Builder.Default
    private List<String> issues = new ArrayList<>();
}

