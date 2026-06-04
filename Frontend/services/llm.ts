import axiosClient from '@/api/axiosClient'

export type GenerateTestRequest = {
  type: string
  description: string
  databaseType?: string
  targetClassName?: string
  suiteId?: number
  testData?: string
  skeleton?: string
  // M1 structured fields
  methodName?: string
  scenarioType?: string
  expectedBehavior?: string
}

export type GenerateTestResponse = {
  generatedCode: string
}

export const llmService = {
  generateTest: async (payload: GenerateTestRequest): Promise<GenerateTestResponse> => {
    const { data } = await axiosClient.post('/api/llm/generate-test', payload)
    return data
  },
}
