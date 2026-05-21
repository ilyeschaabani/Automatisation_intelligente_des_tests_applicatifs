package com.pfe.platform.msexecution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UxEvaluationRequest {
    @NotNull
    private Long projectId;

    @NotNull
    @Size(min = 1)
    private String platform; // WEB or MOBILE

    private String url;

    private String description;
}
