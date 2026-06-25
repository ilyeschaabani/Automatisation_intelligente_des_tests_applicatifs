package com.pfe.platform.ms_gestion.controller;


import com.pfe.platform.ms_gestion.dto.request.AddMemberRequest;
import com.pfe.platform.ms_gestion.dto.response.MemberResponse;
import com.pfe.platform.ms_gestion.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {
    private final ProjectMemberService memberService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<Void> add(@PathVariable Long projectId,
                                    @RequestBody AddMemberRequest request) {
        memberService.addMember(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(memberService.listMembers(projectId));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_MANAGER')")
    public ResponseEntity<Void> remove(@PathVariable Long projectId,
                                       @PathVariable Long userId) {
        memberService.removeMember(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
