package com.pfe.platform.ms_gestion.controller;

import com.pfe.platform.ms_gestion.dto.request.CreateTestCaseRequest;
import com.pfe.platform.ms_gestion.dto.response.TestCaseResponse;
import com.pfe.platform.ms_gestion.service.TestCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suites/{suiteId}/testcases")
@RequiredArgsConstructor
public class TestCaseController {
    private final TestCaseService testCaseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<TestCaseResponse> add(@PathVariable Long suiteId,
                                                @Valid @RequestBody CreateTestCaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testCaseService.add(suiteId, request));
    }

    @GetMapping
    public ResponseEntity<List<TestCaseResponse>> list(@PathVariable Long suiteId) {
        return ResponseEntity.ok(testCaseService.list(suiteId));
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<TestCaseResponse> get(@PathVariable Long suiteId,
                                                @PathVariable Long caseId) {
        return ResponseEntity.ok(testCaseService.get(suiteId, caseId));
    }

    @PutMapping("/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER', 'TESTEUR')")
    public ResponseEntity<TestCaseResponse> update(@PathVariable Long suiteId,
                                                   @PathVariable Long caseId,
                                                   @Valid @RequestBody CreateTestCaseRequest request) {
        return ResponseEntity.ok(testCaseService.update(suiteId, caseId, request));
    }

    @DeleteMapping("/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long suiteId,
                                       @PathVariable Long caseId) {
        testCaseService.delete(suiteId, caseId);
        return ResponseEntity.noContent().build();
    }
}
