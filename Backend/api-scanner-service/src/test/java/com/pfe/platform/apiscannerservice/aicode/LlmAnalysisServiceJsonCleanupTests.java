package com.pfe.platform.apiscannerservice.aicode;

import com.pfe.platform.apiscannerservice.aicode.service.LlmAnalysisService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmAnalysisServiceJsonCleanupTests {

    @Test
    void stripsJsonCodeFences() {
        String raw = "```json\n{\"framework\":\"Spring Boot\",\"confidence\":\"HIGH\",\"evidence\":[\"pom.xml\"]}\n```";
        String cleaned = LlmAnalysisService.cleanupToJson(raw);
        assertTrue(cleaned.startsWith("{"));
        assertTrue(cleaned.endsWith("}"));
        assertFalse(cleaned.contains("```"));
    }

    @Test
    void extractsFirstJsonObjectFromExtraText() {
        String raw = "Sure! Here is the result:\n{\"framework\":\"Express\",\"confidence\":\"MEDIUM\",\"evidence\":[\"package.json\"]}\nThanks!";
        String cleaned = LlmAnalysisService.cleanupToJson(raw);
        assertEquals('{', cleaned.charAt(0));
        assertEquals('}', cleaned.charAt(cleaned.length() - 1));
        assertTrue(cleaned.contains("\"framework\""));
    }

    @Test
    void handlesBracesInsideStrings() {
        String raw = "```json\n{\"framework\":\"X\",\"confidence\":\"LOW\",\"evidence\":[\"text with { brace } inside\"]}\n```";
        String cleaned = LlmAnalysisService.cleanupToJson(raw);
        assertTrue(cleaned.contains("text with { brace } inside"));
        assertTrue(cleaned.startsWith("{"));
        assertTrue(cleaned.endsWith("}"));
    }
}

