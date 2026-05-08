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
  createdAt: string;
}

export interface CreateProjectRequest {
  name: string;          // required
  description?: string;
  gitRepoUrl?: string;
  gitDefaultBranch?: string; // default 'main'
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
  createdAt: string;
}

export interface CreateTestSuiteRequest {
  name: string;
  description?: string;
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