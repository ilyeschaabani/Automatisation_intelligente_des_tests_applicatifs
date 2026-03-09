package com.pfe.platform.apiscannerservice.Model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectMetadata {

    public enum Framework {
        SPRING_BOOT,
        QUARKUS,
        MICRONAUT,

        EXPRESS,
        NESTJS,
        FASTIFY,
        KOA,

        DOTNET,

        FASTAPI,
        DJANGO,
        FLASK,

        LARAVEL,
        SYMFONY,

        RAILS,

        GIN,

        UNKNOWN
    }

    @Builder.Default
    private Framework framework = Framework.UNKNOWN;

    private String language;

    @Builder.Default
    private Map<String, String> hints = new HashMap<>();
}
