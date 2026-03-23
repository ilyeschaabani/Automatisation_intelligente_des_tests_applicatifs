package com.pfe.platform.apiscannerservice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMetadata {

    public enum Framework {
        SPRING_BOOT,
        EXPRESS,
        FASTAPI,
        DJANGO,
        FLASK,
        LARAVEL,
        SYMFONY,
        RAILS,
        GIN,
        QUARKUS,
        MICRONAUT,
        UNKNOWN
    }

    @Builder.Default
    private Framework framework = Framework.UNKNOWN;

    @Builder.Default
    private Map<String, String> hints = new HashMap<>();
}
