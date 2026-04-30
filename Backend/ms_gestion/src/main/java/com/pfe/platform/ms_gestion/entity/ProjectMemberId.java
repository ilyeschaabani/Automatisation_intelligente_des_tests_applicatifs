package com.pfe.platform.ms_gestion.entity;

import java.io.Serializable;
import java.util.Objects;

public class ProjectMemberId implements Serializable {
    private Long projectId;
    private Long userId;

    public ProjectMemberId() {}
    public ProjectMemberId(Long projectId, Long userId) {
        this.projectId = projectId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectMemberId)) return false;
        ProjectMemberId that = (ProjectMemberId) o;
        return Objects.equals(projectId, that.projectId) &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, userId);
    }
}
