// E2E 假 Webhook 接收方：接收 HTTP POST 投递，按 WEBHOOK_STATUS 环境变量返回状态码。
// 由 playwright.config.ts 的 webServer 管理生命周期（端口 18091）。
import { createServer } from 'node:http'

const PORT = 18091
const status = Number(process.env.WEBHOOK_STATUS ?? '200')

const server = createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' })
    res.end('ok')
    return
  }
  if (req.method === 'POST') {
    // 读取并丢弃请求体，返回配置的状态码
    req.on('data', () => {})
    req.on('end', () => {
      res.writeHead(status, { 'Content-Type': 'text/plain' })
      res.end(status >= 200 && status < 300 ? 'ok' : 'fail')
    })
    return
  }
  res.writeHead(404)
  res.end('not found')
})

server.listen(PORT, '127.0.0.1', () => {
  // eslint-disable-next-line no-console
  console.log(`fake-webhook-server listening on 127.0.0.1:${PORT} (status=${status})`)
})
