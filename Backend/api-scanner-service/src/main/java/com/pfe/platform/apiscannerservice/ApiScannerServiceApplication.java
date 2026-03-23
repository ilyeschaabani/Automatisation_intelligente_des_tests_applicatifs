package com.pfe.platform.apiscannerservice;

import com.pfe.platform.apiscannerservice.Service.GitAuthProperties;
import com.pfe.platform.apiscannerservice.aicode.config.AiCodeAnalyzerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GitAuthProperties.class, AiCodeAnalyzerProperties.class})
public class ApiScannerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiScannerServiceApplication.class, args);
    }
}
