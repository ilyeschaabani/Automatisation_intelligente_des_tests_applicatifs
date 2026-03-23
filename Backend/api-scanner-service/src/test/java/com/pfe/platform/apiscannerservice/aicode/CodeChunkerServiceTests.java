package com.pfe.platform.apiscannerservice.aicode;

import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import com.pfe.platform.apiscannerservice.aicode.model.IngestedFile;
import com.pfe.platform.apiscannerservice.aicode.service.CodeChunkerService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeChunkerServiceTests {

    @Test
    void chunksAreBoundedByMaxChars() {
        AiCodeAnalyzerProperties props = new AiCodeAnalyzerProperties();
        props.setMaxChunkChars(10);

        CodeChunkerService svc = new CodeChunkerService(props);
        String content = "0123456789ABCDEFGHIJ"; // 20 chars

        var chunks = svc.chunk(List.of(new IngestedFile(Path.of("A.java"), content)));
        assertEquals(2, chunks.size());
        assertEquals(10, chunks.get(0).text().length());
        assertEquals(10, chunks.get(1).text().length());
        assertEquals("A.java", chunks.get(0).filePath());
    }
}

