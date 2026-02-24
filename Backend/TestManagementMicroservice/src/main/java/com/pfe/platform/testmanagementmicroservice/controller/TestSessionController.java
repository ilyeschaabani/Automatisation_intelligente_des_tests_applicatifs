package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.DTO.TestSessionDto;
import com.pfe.platform.testmanagementmicroservice.entity.TestSession;
import com.pfe.platform.testmanagementmicroservice.service.TestSession.TestSessionMapper;
import com.pfe.platform.testmanagementmicroservice.service.TestSession.TestSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class TestSessionController {

    private final TestSessionService testSessionService;

    public TestSessionController(TestSessionService testSessionService) {
        this.testSessionService = testSessionService;
    }

    @GetMapping
    public List<TestSessionDto> getAll(@RequestParam(name = "projectId", required = false) Long projectId,
                                      @RequestParam(name = "campaignId", required = false) Long campaignId) {
        List<TestSession> sessions;
        if (projectId != null) {
            sessions = testSessionService.findByProject(projectId);
        } else if (campaignId != null) {
            sessions = testSessionService.findByCampaign(campaignId);
        } else {
            sessions = testSessionService.findAll();
        }
        return sessions.stream().map(TestSessionMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public TestSessionDto getById(@PathVariable Long id) {
        return TestSessionMapper.toDto(testSessionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TestSessionDto> create(@RequestParam Long projectId,
                                                @RequestParam(required = false) Long campaignId,
                                                @RequestBody TestSession session) {
        TestSession created = testSessionService.create(projectId, campaignId, session);
        return ResponseEntity.status(HttpStatus.CREATED).body(TestSessionMapper.toDto(created));
    }

    @PutMapping("/{id}")
    public TestSessionDto update(@PathVariable Long id, @RequestBody TestSession session) {
        return TestSessionMapper.toDto(testSessionService.update(id, session));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testSessionService.delete(id);
    }
}
