export type Framework = 'SPRING' | 'EXPRESS' | 'NEST' | 'DOTNET' | 'UNKNOWN'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'

export type ProjectMetadata = {
  framework: Framework
  hints: Record<string, unknown>
}

export type EndpointDefinition = {
  method: HttpMethod
  path: string
  controller?: string
  handler?: string
}

export type ApiContract = {
  source: string
  metadata: ProjectMetadata
  generatedAt: string
  endpoints: EndpointDefinition[]
  issues: string[]
}
