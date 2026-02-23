package com.pfe.platform.testmanagementmicroservice.service.Project;

import com.pfe.platform.testmanagementmicroservice.DTO.ProjectCreateRequest;
import com.pfe.platform.testmanagementmicroservice.entity.Project;

import java.util.List;

public interface ProjectService {
     List<Project> findAll();
     Project findById(Long id) ;
    Project create(ProjectCreateRequest req);
    Project update(Long id, Project incoming);
    void delete(Long id);
}
