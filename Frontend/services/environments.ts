import axiosClient from '@/api/axiosClient';
import type {
  Environment,
  CreateEnvironmentRequest,
  UpdateEnvironmentRequest,
} from '@/types/ms-gestion';

export const environmentService = {
  getAll: async (projectId: number): Promise<Environment[]> => {
    const { data } = await axiosClient.get(`/api/projects/${projectId}/environments`);
    return data;
  },
  getById: async (projectId: number, envId: number): Promise<Environment> => {
    const { data } = await axiosClient.get(`/api/projects/${projectId}/environments/${envId}`);
    return data;
  },
  create: async (projectId: number, payload: CreateEnvironmentRequest): Promise<Environment> => {
    const { data } = await axiosClient.post(`/api/projects/${projectId}/environments`, payload);
    return data;
  },
  update: async (
    projectId: number,
    envId: number,
    payload: UpdateEnvironmentRequest,
  ): Promise<Environment> => {
    const { data } = await axiosClient.put(`/api/projects/${projectId}/environments/${envId}`, payload);
    return data;
  },
  delete: async (projectId: number, envId: number): Promise<void> => {
    await axiosClient.delete(`/api/projects/${projectId}/environments/${envId}`);
  },
};