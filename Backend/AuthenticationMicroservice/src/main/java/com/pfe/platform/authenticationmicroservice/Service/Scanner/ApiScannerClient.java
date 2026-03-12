package com.pfe.platform.authenticationmicroservice.Service.Scanner;

import com.pfe.platform.authenticationmicroservice.Dto.ScannerRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class ApiScannerClient {

    private final WebClient webClient;

    public ApiScannerClient(
            WebClient.Builder builder,
            @Value("${services.api-scanner.base-url:${API_SCANNER_SERVICE_URL:http://localhost:8099}}") String baseUrl,
            @Value("${services.api-scanner.timeout:5m}") Duration timeout
    ) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .build();
        this.timeout = timeout;
    }

    private final Duration timeout;

    /**
     * Calls api-scanner-service POST /scan.
     *
     * @return raw JSON body as String (pass-through)
     * @throws WebClientResponseException if non-2xx
     */
    public String scan(ScannerRequest request) throws WebClientResponseException {
        return webClient.post()
                .uri("/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                // keep body for error propagation
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .onErrorResume(e -> Mono.error(e))
                .block();
    }
}

