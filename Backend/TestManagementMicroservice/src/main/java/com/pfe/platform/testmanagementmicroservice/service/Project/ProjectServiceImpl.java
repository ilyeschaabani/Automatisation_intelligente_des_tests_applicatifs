package com.pfe.platform.testmanagementmicroservice.service.Project;

import com.pfe.platform.testmanagementmicroservice.DTO.ProjectCreateRequest;
import com.pfe.platform.testmanagementmicroservice.entity.Enum.RepoProvider;
import com.pfe.platform.testmanagementmicroservice.entity.Project;
import com.pfe.platform.testmanagementmicroservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
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
        validateRepoUrl(req.repoProvider(), req.repositoryUrl());

        Project p = new Project();
        p.setName(req.name());
        p.setDescription(blankToNull(req.description()));
        p.setRepositoryUrl(blankToNull(req.repositoryUrl()));
        p.setType(req.type());
        p.setDefaultBranch(blankToNull(req.defaultBranch()));
        p.setRepoProvider(req.repoProvider());
        p.setArchived(Boolean.TRUE.equals(req.archived()));

        return projectRepository.save(p);
    }


    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }


    private static void validateRepoUrl(RepoProvider provider, String repositoryUrl) {
        if (provider == null) return;
        if (repositoryUrl == null || repositoryUrl.isBlank()) return;

        URI uri;
        try {
            uri = URI.create(repositoryUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid repositoryUrl");
        }

        String host = uri.getHost();
        if (host == null) throw new IllegalArgumentException("Invalid repositoryUrl host");

        String h = host.toLowerCase();
        if (provider == RepoProvider.GITHUB && !h.endsWith("github.com")) {
            throw new IllegalArgumentException("repoProvider is GITHUB but URL host is not github.com");
        }
        if (provider == RepoProvider.GITLAB && !h.contains("gitlab")) {
            throw new IllegalArgumentException("repoProvider is GITLAB but URL host is not GitLab");
        }
    }

    @Override
    public Project update(Long id, Project incoming) {
        Project existing = findById(id);
        existing.setName(incoming.getName());
        existing.setDescription(incoming.getDescription());
        existing.setRepositoryUrl(incoming.getRepositoryUrl());
        existing.setType(incoming.getType());
        existing.setCreatedBy(incoming.getCreatedBy());
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
