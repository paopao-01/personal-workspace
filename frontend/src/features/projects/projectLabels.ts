import type { EvidenceType } from '@/api/projects/projectApi'

export const evidenceTypeLabels: Record<EvidenceType, string> = {
  PROJECT_CODE: '项目代码',
  GIT_REPOSITORY: 'Git 仓库',
  ARTICLE: '技术文章',
  ARCHITECTURE_DIAGRAM: '架构图',
  API_DOCUMENT: '接口文档',
  LOAD_TEST_REPORT: '压测报告',
  LOG_OR_MONITORING: '日志或监控截图',
  INTERVIEW_ANSWER: '面试回答',
  WORK_EXPERIENCE: '工作经历',
}

export const evidenceTypeOptions = Object.entries(evidenceTypeLabels) as [
  EvidenceType,
  string,
][]
