package com.pfe.platform.testmanagementmicroservice.service.Project;

import com.pfe.platform.testmanagementmicroservice.DTO.ProjectCreateRequest;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    @Override
    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    @Override
    public Project findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
    }

    @Override
    public Project create(ProjectCreateRequest req) {
        Project p = new Project();
        p.setName(req.name());
        p.setProjectType(req.projectType());
        p.setSourceType(req.sourceType());
        p.setRepositoryUrl(blankToNull(req.repositoryUrl()));
        p.setGitTokenId(blankToNull(req.gitTokenId()));
        p.setTechnologyStack(blankToNull(req.technologyStack()));
        p.setDefaultBranch(blankToNull(req.defaultBranch()));
        p.setDeployed(Boolean.TRUE.equals(req.deployed()));
        return projectRepository.save(p);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    @Override
    public Project update(Long id, Project incoming) {
        Project existing = findById(id);
        existing.setName(incoming.getName());
        existing.setProjectType(incoming.getProjectType());
        existing.setSourceType(incoming.getSourceType());
        existing.setRepositoryUrl(incoming.getRepositoryUrl());
        existing.setGitTokenId(incoming.getGitTokenId());
        existing.setTechnologyStack(incoming.getTechnologyStack());
        existing.setDefaultBranch(blankToNull(incoming.getDefaultBranch()));
        existing.setDeployed(incoming.isDeployed());
        return projectRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new IllegalArgumentException("Project not found: " + id);
        }
        projectRepository.deleteById(id);
    }
}
