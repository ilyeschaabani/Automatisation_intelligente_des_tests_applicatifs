package com.pfe.platform.msexecution.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private String label;
    private int passed;
    private int failed;
    private int skipped;
}