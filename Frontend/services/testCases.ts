import axiosClient from '@/api/axiosClient';
import type { TestCase, CreateTestCaseRequest, UpdateTestCaseRequest } from '@/types/ms-gestion';

export const testCaseService = {
  getAll: async (suiteId: number): Promise<TestCase[]> => {
    const { data } = await axiosClient.get(`/api/suites/${suiteId}/testcases`);
    return data;
  },
  getById: async (suiteId: number, caseId: number): Promise<TestCase> => {
    const { data } = await axiosClient.get(`/api/suites/${suiteId}/testcases/${caseId}`);
    return data;
  },
  create: async (suiteId: number, payload: CreateTestCaseRequest): Promise<TestCase> => {
    const { data } = await axiosClient.post(`/api/suites/${suiteId}/testcases`, payload);
    return data;
  },
  update: async (suiteId: number, caseId: number, payload: UpdateTestCaseRequest): Promise<TestCase> => {
    const { data } = await axiosClient.put(`/api/suites/${suiteId}/testcases/${caseId}`, payload);
    return data;
  },
  delete: async (suiteId: number, caseId: number): Promise<void> => {
    await axiosClient.delete(`/api/suites/${suiteId}/testcases/${caseId}`);
  },
};