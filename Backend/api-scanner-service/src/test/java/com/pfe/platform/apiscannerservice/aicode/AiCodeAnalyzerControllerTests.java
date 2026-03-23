package com.pfe.platform.apiscannerservice.aicode;

import com.pfe.platform.apiscannerservice.aicode.model.AnalyzeResponse;
import com.pfe.platform.apiscannerservice.aicode.service.AiCodeAnalyzerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "aicode.ollama.base-url=http://localhost:11434",
        "aicode.git.temp-root=${java.io.tmpdir}"
})
@AutoConfigureMockMvc
class AiCodeAnalyzerControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AiCodeAnalyzerService service;

    @Test
    void analyzeReturnsJson() throws Exception {
        when(service.analyzeRepo(anyString(), any())).thenReturn(
                new AnalyzeResponse("Spring Boot", AnalyzeResponse.Confidence.HIGH, List.of("pom.xml contains spring-boot-starter-web"))
        );

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/repo.git\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.framework").value("Spring Boot"));
    }
}

