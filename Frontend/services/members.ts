import axiosClient from '@/api/axiosClient';
import type { Member, AddMemberRequest } from '@/types/ms-gestion';

export const memberService = {
  getAll: async (projectId: number): Promise<Member[]> => {
    const { data } = await axiosClient.get(`/api/projects/${projectId}/members`);
    return data;
  },
  add: async (projectId: number, payload: AddMemberRequest): Promise<Member> => {
    const { data } = await axiosClient.post(`/api/projects/${projectId}/members`, payload);
    return data;
  },
  remove: async (projectId: number, userId: number): Promise<void> => {
    await axiosClient.delete(`/api/projects/${projectId}/members/${userId}`);
  },
};