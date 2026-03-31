package com.pfe.platform.testmanagementmicroservice.controller;

import com.pfe.platform.testmanagementmicroservice.DTO.EndpointDto;
import com.pfe.platform.testmanagementmicroservice.DTO.ProjectCreateRequest;
import com.pfe.platform.testmanagementmicroservice.DTO.ProjectDto;
import com.pfe.platform.testmanagementmicroservice.entity.Endpoint;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.service.Discovery.DiscoveryServiceimpl;
import com.pfe.platform.testmanagementmicroservice.service.Project.ProjectMapper;
import com.pfe.platform.testmanagementmicroservice.service.Project.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final DiscoveryServiceimpl discoveryService;

    public ProjectController(ProjectService projectService ,DiscoveryServiceimpl discoveryService ) {

        this.projectService = projectService;
        this.discoveryService = discoveryService;
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
}
