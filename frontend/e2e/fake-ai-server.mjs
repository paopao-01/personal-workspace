// E2E 假 AI 供应商：OpenAI 兼容 /chat/completions，返回固定候选要求 JSON。
// 由 playwright.config.ts 的 webServer 管理生命周期（端口 18090）。
import { createServer } from 'node:http'

const CANDIDATES = [
  { type: 'MUST', rawText: '熟悉 Spring Boot 与 MySQL（E2E 假供应商输出）', normalizedName: 'Spring Boot/MySQL', proficiencyText: '熟练' },
  { type: 'BONUS', rawText: '有 Redis 高并发经验（E2E 假供应商输出）', normalizedName: 'Redis 高并发', proficiencyText: '' },
]

const QUESTION_CLASSIFICATION = [
  {
    type: 'TECHNICAL',
    rawText: 'Redis 持久化机制如何选择？（E2E 分类输出）',
    normalizedName: '技术基础',
    rationale: '问题要求解释具体技术机制。',
  },
]

const ANSWER_QUALITY_ANALYSIS = [
  {
    type: 'ANSWER_QUALITY',
    rawText: '覆盖了基本机制，但缺少场景取舍。',
    normalizedName: '回答质量分析',
    answerStatus: 'PARTIALLY_ANSWERED',
    referenceAnswer: '先说明机制，再比较适用场景、风险和恢复策略。',
    errorReason: '缺少边界条件和方案取舍。',
    improvementPlan: '按机制、场景、取舍、案例四步重新组织回答。',
    rationale: '原回答只描述了基本操作。',
  },
]

const TASK_SUGGESTION = [
  {
    type: 'LEARNING_TASK',
    rawText: '围绕原问题完成一次可验证的口述演练。',
    taskTitle: '补齐缓存一致性回答',
    priority: 'HIGH',
    estimatedMinutes: 45,
    learningGoal: '能够解释核心机制、风险和改进方案。',
    acceptanceCriteria: '能在 3 分钟内完整回答原问题并说明一个边界场景。',
    verificationMethod: '口述演练并记录验证结果',
    rationale: '问题回答存在薄弱点，需要通过练习形成可复用表达。',
  },
]
const MOCK_INTERVIEW = [
  { type: 'MOCK_INTERVIEW_OPENING', rawText: '我会按项目场景、采取方案、解决问题和结果进行讲解。', rationale: '请说明这个方案的关键取舍。' },
]
const MOCK_INTERVIEW_FOLLOW_UP = [
  { type: 'MOCK_INTERVIEW_FOLLOW_UP', rawText: '如果异步削峰后的消息积压，你会如何监控并处理？' },
]
const MOCK_INTERVIEW_ANSWER_EVALUATION = [
  { type: 'MOCK_INTERVIEW_ANSWER_EVALUATION', rawText: '回答覆盖了场景和方案取舍；可补充具体的监控指标与降级动作。', normalizedName: '4', rationale: '能说明约束和取舍，但缺少可验证的处置细节。' },
]

const server = createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200).end('ok')
    return
  }
  if (req.method === 'POST' && req.url?.includes('/chat/completions')) {
    let raw = ''
    req.on('data', (chunk) => {
      raw += chunk
    })
    req.on('end', () => {
      const body = JSON.parse(raw || '{}')
      if (!body.model) {
        res.writeHead(400, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ error: { message: 'model required' } }))
        return
      }
      const requestText = JSON.stringify(body)
      const content = requestText.includes('MOCK_INTERVIEW_ANSWER_EVALUATION')
        ? MOCK_INTERVIEW_ANSWER_EVALUATION
        : requestText.includes('MOCK_INTERVIEW_FOLLOW_UP')
        ? MOCK_INTERVIEW_FOLLOW_UP
        : requestText.includes('Java 项目面试官')
        ? MOCK_INTERVIEW
        : requestText.includes('LEARNING_TASK')
        ? TASK_SUGGESTION
        : requestText.includes('ANSWER_QUALITY')
        ? ANSWER_QUALITY_ANALYSIS
        : requestText.includes('PROJECT_EXPERIENCE')
          ? QUESTION_CLASSIFICATION
          : CANDIDATES
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ choices: [{ message: { role: 'assistant', content: JSON.stringify(content) } }] }))
    })
    return
  }
  res.writeHead(404).end('not found')
})

server.listen(18090, '127.0.0.1')
