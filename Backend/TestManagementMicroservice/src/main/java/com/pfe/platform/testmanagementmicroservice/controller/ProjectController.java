package com.pfe.platform.testmanagementmicroservice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.platform.testmanagementmicroservice.DTO.DiscoveryCompleteRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.DiscoveryFlowDto;
import com.pfe.platform.testmanagementmicroservice.DTO.EndpointDto;
import com.pfe.platform.testmanagementmicroservice.DTO.ProjectCreateRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.ProjectDto;
import com.pfe.platform.testmanagementmicroservice.entity.Discovery;
import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.service.Discovery.DiscoveryServiceimpl;
import com.pfe.platform.testmanagementmicroservice.service.Project.ProjectMapper;
import com.pfe.platform.testmanagementmicroservice.service.Project.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final DiscoveryServiceimpl discoveryService;
    private final ObjectMapper objectMapper;

    public ProjectController(ProjectService projectService, DiscoveryServiceimpl discoveryService, ObjectMapper objectMapper) {

        this.projectService = projectService;
        this.discoveryService = discoveryService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<ProjectDto> getAll() {
        return projectService.findAll().stream().map(ProjectMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public ProjectDto getById(@PathVariable Long id) {
        return ProjectMapper.toDto(projectService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Validated @RequestBody ProjectCreateRequest req) {
        Project created = projectService.create(req);
        return ProjectMapper.toDto(created);
    }

    @PutMapping("/{id}")
    public ProjectDto update(@PathVariable Long id, @RequestBody Project project) {
        return ProjectMapper.toDto(projectService.update(id, project));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }


    @GetMapping("/{id}/endpoints")
    public List<EndpointDto> getProjectEndpoints(@PathVariable Long id,
                                                 @RequestParam(required = false) String branch) {
        // get or start discoverywait
        List<Endpoint> endpoints = discoveryService.getOrStartDiscovery(id, branch);

        // map to DTOs (create EndpointDto if not already)
        return endpoints.stream().map(EndpointDto::fromEntity).toList();
    }

    // New discovery UX: start -> poll -> (maybe) complete -> endpoints.
    @PostMapping("/{id}/discoveries")
    public DiscoveryFlowDto startDiscovery(@PathVariable Long id, @RequestParam(required = false) String branch) {
        Discovery d = discoveryService.startDiscoveryFlow(id, branch);
        return toFlowDto(d);
    }

    @GetMapping("/{id}/discoveries/latest")
    public DiscoveryFlowDto getLatestDiscovery(@PathVariable Long id, @RequestParam(required = false) String branch) {
        Discovery d = discoveryService.getLatestDiscovery(id, branch);
        return toFlowDto(d);
    }

    @PostMapping("/{id}/discoveries/{discoveryId}/complete")
    public DiscoveryFlowDto completeDiscovery(
            @PathVariable Long id,
            @PathVariable Long discoveryId,
            @RequestBody DiscoveryCompleteRequest request
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("answers", request != null ? request.answers() : null);
        if (request != null && request.overwrite() != null) body.put("overwrite", request.overwrite());
        if (request != null && request.return_openapi() != null) body.put("return_openapi", request.return_openapi());

        Discovery d = discoveryService.completeDiscoveryFlow(discoveryId, body);
        return toFlowDto(d);
    }

    private DiscoveryFlowDto toFlowDto(Discovery d) {
        if (d == null) {
            return new DiscoveryFlowDto(null, "none", null, null, List.of(), null);
        }

        Object questionnaire = null;
        if (d.getContractQuestionnaireJson() != null && !d.getContractQuestionnaireJson().isBlank()) {
            try {
                questionnaire = objectMapper.readValue(
                        d.getContractQuestionnaireJson(),
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception ignored) {
                questionnaire = null;
            }
        }

        List<EndpointDto> endpoints = d.getEndpoints() != null
                ? d.getEndpoints().stream().map(EndpointDto::fromEntity).toList()
                : List.of();

        return new DiscoveryFlowDto(
                d.getId(),
                d.getStatus(),
                d.getFastApiJobId(),
                questionnaire,
                endpoints,
                d.getError()
        );
    }
}
