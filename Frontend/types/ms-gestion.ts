export type ProjectStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type TestType = 'WEB' | 'API' | 'UNIT' | 'INTEGRATION';
export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type MemberRole = 'ADMIN' | 'TESTER' | 'DEVOPS';

export interface Project {
  id: number;
  name: string;
  description?: string;
  gitRepoUrl?: string;
  gitDefaultBranch: string;
  status: ProjectStatus;
  aiBuiltin?: boolean; // True when project uses AI-built template (no Git repo)
  aiProject?: boolean; // True when project is considered AI (no git repo)
  createdAt: string;
}

export interface CreateProjectRequest {
  name: string;          // required
  description?: string;
  gitRepoUrl?: string;
  gitDefaultBranch?: string; // default 'main'
  aiBuiltin?: boolean; // True to use internal Maven template without Git
}

export interface UpdateProjectRequest extends CreateProjectRequest {}

export interface Environment {
  id: number;
  name: string;
  baseUrlWeb?: string;
  baseUrlApi?: string;
  variables?: string; // JSON string
  createdAt: string;
}

export interface CreateEnvironmentRequest {
  name: string;
  baseUrlWeb?: string;
  baseUrlApi?: string;
  variables?: string;
}

export interface UpdateEnvironmentRequest extends CreateEnvironmentRequest {}

export interface TestSuite {
  id: number;
  name: string;
  description?: string;
  type?: TestType;
  gitRepoUrl?: string;
  gitBranch?: string;
  modulePath?: string;
  createdAt: string;
}

export interface CreateTestSuiteRequest {
  name: string;
  type?: TestType;
  description?: string;
  gitRepoUrl?: string;
  gitBranch?: string;
  modulePath?: string;
}

export interface UpdateTestSuiteRequest extends CreateTestSuiteRequest {}

export interface TestCase {
  id: number;
  suiteId: number;
  title: string;
  description?: string;
  type: TestType;
  priority?: number;
  riskLevel?: RiskLevel;
  scriptPath?: string;
  // Fields for AI-generated tests
  generated?: boolean;
  generatedCode?: string | null;
  useAI?: boolean;
  testData?: string; // JSON string
  tags?: string;
  maxDurationSeconds?: number;
  active: boolean;
  flaky: boolean;
  createdAt: string;
}

export interface CreateTestCaseRequest {
  title: string;
  description?: string;
  type: TestType;        // required
  priority?: number;
  riskLevel?: RiskLevel;
  scriptPath?: string;
  // AI-related fields
  useAI?: boolean;
  descriptionAI?: string;
  generatedCode?: string;
  generated?: boolean;
  testData?: string;
  tags?: string;
  maxDurationSeconds?: number;
}

export interface UpdateTestCaseRequest extends CreateTestCaseRequest {}

export interface Member {
  userId: number;
  role: MemberRole;
}

export interface AddMemberRequest {
  userId: number;
  role: MemberRole;
}