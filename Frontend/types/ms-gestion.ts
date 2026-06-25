export type ProjectStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type TestType = 'WEB' | 'UNIT' | 'INTEGRATION';
export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export interface Project {
  id: number;
  name: string;
  description?: string;
  gitRepoUrl?: string;
  gitDefaultBranch: string;
  status: ProjectStatus;
  aiBuiltin?: boolean; // True when project uses AI-built template (no Git repo)
  aiProject?: boolean; // True when project is considered AI (no git repo)
  createdBy?: number;
  createdAt: string;
}

export interface CreateProjectRequest {
  name: string;          // required
  description?: string;
}

export interface UpdateProjectRequest extends CreateProjectRequest {}

export interface Environment {
  id: number;
  name: string;
  baseUrlWeb?: string;
  baseUrlApi?: string;
  gitRepoUrl?: string;   // source code repo (UNIT/INTEGRATION tests)
  gitBranch?: string;    // branch of source code repo
  databaseType?: string; // POSTGRESQL | MYSQL | H2 | MONGODB
  createdAt: string;
}

export interface CreateEnvironmentRequest {
  name: string;
  baseUrlWeb?: string;
  baseUrlApi?: string;
  gitRepoUrl?: string;
  gitBranch?: string;
  databaseType?: string;
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
  gitRepoUrl?: string;
  springProfile?: string;
  databaseType?: string;
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
  gitRepoUrl?: string;
  springProfile?: string;
  databaseType?: string; // POSTGRESQL | MYSQL | H2 | MONGODB
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
  targetClassName?: string;
}

export interface UpdateTestCaseRequest extends CreateTestCaseRequest {}

export interface Member {
  userId: number;
}

export interface AddMemberRequest {
  userId: number;
}