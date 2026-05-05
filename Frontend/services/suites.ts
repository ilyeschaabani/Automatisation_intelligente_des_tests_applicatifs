import axiosClient from '@/api/axiosClient';
import type { TestSuite, CreateTestSuiteRequest, UpdateTestSuiteRequest } from '@/types/ms-gestion';

export const testSuiteService = {
  getAll: async (projectId: number): Promise<TestSuite[]> => {
    const { data } = await axiosClient.get(`/api/projects/${projectId}/suites`);
    return data;
  },
  getById: async (projectId: number, suiteId: number): Promise<TestSuite> => {
    const { data } = await axiosClient.get(`/api/projects/${projectId}/suites/${suiteId}`);
    return data;
  },
  create: async (projectId: number, payload: CreateTestSuiteRequest): Promise<TestSuite> => {
    const { data } = await axiosClient.post(`/api/projects/${projectId}/suites`, payload);
    return data;
  },
  update: async (projectId: number, suiteId: number, payload: UpdateTestSuiteRequest): Promise<TestSuite> => {
    const { data } = await axiosClient.put(`/api/projects/${projectId}/suites/${suiteId}`, payload);
    return data;
  },
  delete: async (projectId: number, suiteId: number): Promise<void> => {
    await axiosClient.delete(`/api/projects/${projectId}/suites/${suiteId}`);
  },
};