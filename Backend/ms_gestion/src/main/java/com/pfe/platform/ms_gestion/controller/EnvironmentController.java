package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.request.CreateEnvironmentRequest;
import com.pfe.platform.ms_gestion.dto.response.EnvironmentResponse;
import com.pfe.platform.ms_gestion.service.EnvironmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/environments")
@RequiredArgsConstructor
public class EnvironmentController {
    private final EnvironmentService environmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<EnvironmentResponse> add(@PathVariable Long projectId,
                                                   @Valid @RequestBody CreateEnvironmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(environmentService.add(projectId, request));
    }

    @GetMapping
    public ResponseEntity<List<EnvironmentResponse>> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(environmentService.list(projectId));
    }

    @GetMapping("/{envId}")
    public ResponseEntity<EnvironmentResponse> get(@PathVariable Long projectId,
                                                   @PathVariable Long envId) {
        return ResponseEntity.ok(environmentService.get(projectId, envId));
    }

    @PutMapping("/{envId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<EnvironmentResponse> update(@PathVariable Long projectId,
                                                      @PathVariable Long envId,
                                                      @Valid @RequestBody CreateEnvironmentRequest request) {
        return ResponseEntity.ok(environmentService.update(projectId, envId, request));
    }

    @DeleteMapping("/{envId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long projectId,
                                       @PathVariable Long envId) {
        environmentService.delete(projectId, envId);
        return ResponseEntity.noContent().build();
    }

}
