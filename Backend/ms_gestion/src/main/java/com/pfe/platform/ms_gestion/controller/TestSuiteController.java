package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.request.CreateTestSuiteRequest;
import com.pfe.platform.ms_gestion.dto.response.TestSuiteResponse;
import com.pfe.platform.ms_gestion.service.TestSuiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/suites")
@RequiredArgsConstructor
public class TestSuiteController {
    private final TestSuiteService suiteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<TestSuiteResponse> add(@PathVariable Long projectId,
                                                 @Valid @RequestBody CreateTestSuiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(suiteService.add(projectId, request));
    }

    @GetMapping
    public ResponseEntity<List<TestSuiteResponse>> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(suiteService.list(projectId));
    }

    @GetMapping("/{suiteId}")
    public ResponseEntity<TestSuiteResponse> get(@PathVariable Long projectId,
                                                 @PathVariable Long suiteId) {
        return ResponseEntity.ok(suiteService.get(projectId, suiteId));
    }

    @PutMapping("/{suiteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<TestSuiteResponse> update(@PathVariable Long projectId,
                                                    @PathVariable Long suiteId,
                                                    @Valid @RequestBody CreateTestSuiteRequest request) {
        return ResponseEntity.ok(suiteService.update(projectId, suiteId, request));
    }

    @DeleteMapping("/{suiteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long projectId,
                                       @PathVariable Long suiteId) {
        suiteService.delete(projectId, suiteId);
        return ResponseEntity.noContent().build();
    }
}
