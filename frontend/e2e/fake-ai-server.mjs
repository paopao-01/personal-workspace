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
      const content = JSON.stringify(body).includes('PROJECT_EXPERIENCE')
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
