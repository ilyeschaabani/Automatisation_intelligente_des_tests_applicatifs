package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.dto.KpiResponse;
import com.pfe.platform.msexecution.dto.TrendPoint;
import com.pfe.platform.msexecution.service.KpiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kpi")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class KpiController {

    private final KpiService kpiService;

    @GetMapping("/project/{projectId}")
    public ResponseEntity<KpiResponse> getProjectKpi(@PathVariable Long projectId) {
        return ResponseEntity.ok(kpiService.getKpi(projectId));
    }

    /**
     * Trend points are built from the 4 latest campaigns of the project, ordered chronologically for charting.
     */
    @GetMapping("/trend/{projectId}")
    public ResponseEntity<List<TrendPoint>> getTrend(@PathVariable Long projectId) {
        return ResponseEntity.ok(kpiService.getTrend(projectId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ms-execution"));
    }
}