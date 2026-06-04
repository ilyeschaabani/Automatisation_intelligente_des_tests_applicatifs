import axiosClient from '@/api/axiosClient';
import type { Project, CreateProjectRequest, UpdateProjectRequest } from '@/types/ms-gestion';

export const projectService = {
  getAll: async (): Promise<Project[]> => {
    const { data } = await axiosClient.get('/api/projects');
    return data;
  },
  getById: async (id: number): Promise<Project> => {
    const { data } = await axiosClient.get(`/api/projects/${id}`);
    return data;
  },
  create: async (payload: CreateProjectRequest): Promise<Project> => {
    const { data } = await axiosClient.post('/api/projects', payload);
    return data;
  },
  update: async (id: number, payload: UpdateProjectRequest): Promise<Project> => {
    const { data } = await axiosClient.put(`/api/projects/${id}`, payload);
    return data;
  },
  delete: async (id: number): Promise<void> => {
    await axiosClient.delete(`/api/projects/${id}`);
  },
  archive: async (id: number): Promise<void> => {
    await axiosClient.patch(`/api/projects/${id}/archive`);
  },
};