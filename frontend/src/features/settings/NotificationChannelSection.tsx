import { useState, type FormEvent } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useTestNotificationChannel,
  useUpdateNotificationChannel,
} from '@/api/notifications/useChannelMutations'
import { useNotificationChannel } from '@/api/notifications/useChannelQueries'
import type { NotificationChannelConfig, WebhookChannelConfig } from '@/api/notifications/channelApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'

type ChannelData = NonNullable<ReturnType<typeof useNotificationChannel>['data']>

function permissionLabel() {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return '当前浏览器不支持系统通知'
  }
  if (Notification.permission === 'granted') return '浏览器已授权'
  if (Notification.permission === 'denied') return '浏览器已拒绝（需在浏览器设置中重置后刷新）'
  return '尚未授权（启用后浏览器将弹出授权询问）'
}

function BrowserChannelCard() {
  const channelQuery = useNotificationChannel('BROWSER')
  const updateChannel = useUpdateNotificationChannel('BROWSER')
  const testChannel = useTestNotificationChannel('BROWSER')

  if (channelQuery.isLoading) return <Spinner label="加载浏览器通知配置…" />
  if (channelQuery.error || !channelQuery.data) {
    return <ErrorState error={channelQuery.error ?? new Error('加载失败')} onRetry={() => channelQuery.refetch()} />
  }
  const channel = channelQuery.data

  const toggle = async () => {
    try {
      await updateChannel.mutateAsync({ version: channel.version, body: { enabled: !channel.enabled } })
      pushToast(channel.enabled ? '浏览器通知已停用' : '浏览器通知已启用')
    } catch (caught) {
      pushToast(isApiError(caught) || isNetworkError(caught) ? caught.message : '保存失败，请稍后重试', 'error')
    }
  }

  const runTest = async () => {
    try {
      await testChannel.mutateAsync()
      pushToast('测试通知已创建；若浏览器已授权，将在展示后自动回执')
    } catch (caught) {
      pushToast(isApiError(caught) || isNetworkError(caught) ? caught.message : '测试失败，请稍后重试', 'error')
    }
  }

  return (
    <div className="requirement-row" style={{ flexWrap: 'wrap' }}>
      <div className="requirement-main">
        <span className="requirement-raw">浏览器通知</span>
        <span className="muted">{permissionLabel()}；站内通知始终保留兜底。</span>
      </div>
      <div className="requirement-actions">
        <Badge variant={channel.enabled ? 'success' : 'neutral'}>{channel.enabled ? '已启用' : '未启用'}</Badge>
        <Button size="sm" variant="ghost" type="button" disabled={updateChannel.isPending} onClick={toggle}>
          {channel.enabled ? '停用' : '启用'}
        </Button>
        <Button size="sm" variant="default" type="button" disabled={testChannel.isPending} onClick={runTest}>
          {testChannel.isPending ? '发送中…' : '发送测试通知'}
        </Button>
      </div>
    </div>
  )
}

function EmailChannelCard({ channel }: { channel: ChannelData }) {
  const emailConfig =
    channel.config && channel.config.channelType === 'EMAIL' ? channel.config : null
  const [smtpHost, setSmtpHost] = useState(emailConfig?.smtpHost ?? '')
  const [smtpPort, setSmtpPort] = useState(emailConfig?.smtpPort != null ? String(emailConfig.smtpPort) : '')
  const [username, setUsername] = useState(emailConfig?.username ?? '')
  const [password, setPassword] = useState('')
  const [fromAddress, setFromAddress] = useState(emailConfig?.fromAddress ?? '')
  const [toAddress, setToAddress] = useState(emailConfig?.toAddress ?? '')
  const [useStartTls, setUseStartTls] = useState(emailConfig?.useStartTls ?? false)
  const updateChannel = useUpdateNotificationChannel('EMAIL')
  const testChannel = useTestNotificationChannel('EMAIL')
  const [testResult, setTestResult] = useState<{ status: string; failureReason: string | null } | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const body: { enabled: boolean; config?: NotificationChannelConfig } = {
      enabled: channel.enabled,
    }
    const trimmedPort = smtpPort.trim()
    const emailPayload: Extract<NotificationChannelConfig, { channelType: 'EMAIL' }> = {
      channelType: 'EMAIL',
      smtpHost: smtpHost.trim() || null,
      smtpPort: trimmedPort ? Number(trimmedPort) : null,
      username: username.trim() || null,
      // 密码留空表示保留既有凭据
      password: password ? password : null,
      fromAddress: fromAddress.trim() || null,
      toAddress: toAddress.trim() || null,
      useStartTls,
    }
    body.config = emailPayload
    try {
      await updateChannel.mutateAsync({ version: channel.version, body })
      setPassword('')
      pushToast('邮件渠道配置已保存')
    } catch (caught) {
      pushToast(isApiError(caught) || isNetworkError(caught) ? caught.message : '保存失败，请稍后重试', 'error')
    }
  }

  const runTest = async () => {
    setTestResult(null)
    try {
      const result = await testChannel.mutateAsync()
      const delivery = result.deliveries?.[0]
      if (!delivery) {
        setTestResult({ status: 'FAILED', failureReason: '未生成投递记录' })
        return
      }
      setTestResult({ status: delivery.status, failureReason: delivery.failureReason ?? null })
    } catch (caught) {
      const message = isApiError(caught) || isNetworkError(caught) ? caught.message : '测试失败，请稍后重试'
      setTestResult({ status: 'FAILED', failureReason: message })
    }
  }

  return (
    <div style={{ marginTop: 12 }}>
      <div className="requirement-row" style={{ flexWrap: 'wrap' }}>
        <div className="requirement-main">
          <span className="requirement-raw">邮件提醒</span>
          <span className="muted">
            使用你自己的 SMTP 邮箱发送；密码仅保存在本地数据库，不参与导出。站内通知始终保留兜底。
          </span>
        </div>
        <div className="requirement-actions">
          <Badge variant={channel.enabled ? 'success' : 'neutral'}>{channel.enabled ? '已启用' : '未启用'}</Badge>
          <Button
            size="sm"
            variant="ghost"
            type="button"
            disabled={updateChannel.isPending}
            onClick={async () => {
              try {
                await updateChannel.mutateAsync({ version: channel.version, body: { enabled: !channel.enabled } })
                pushToast(channel.enabled ? '邮件渠道已停用' : '邮件渠道已启用')
              } catch (caught) {
                pushToast(isApiError(caught) || isNetworkError(caught) ? caught.message : '保存失败，请稍后重试', 'error')
              }
            }}
          >
            {channel.enabled ? '停用' : '启用'}
          </Button>
        </div>
      </div>
      <form onSubmit={submit} noValidate style={{ marginTop: 12 }}>
        <div className="form-row">
          <Field label="SMTP 主机" required>
            <Input value={smtpHost} onChange={(event) => setSmtpHost(event.target.value)} maxLength={200}
              placeholder="例如 smtp.example.com" />
          </Field>
          <Field label="SMTP 端口">
            <Input type="number" value={smtpPort} onChange={(event) => setSmtpPort(event.target.value)} min={1} max={65535}
              placeholder="例如 465" />
          </Field>
        </div>
        <div className="form-row">
          <Field label="用户名">
            <Input value={username} onChange={(event) => setUsername(event.target.value)} maxLength={200} autoComplete="off" />
          </Field>
          <Field label="密码">
            <Input type="password" value={password} onChange={(event) => setPassword(event.target.value)} maxLength={200}
              placeholder={channel.hasCredential ? '已保存（留空保持不变）' : ''} autoComplete="new-password" />
          </Field>
        </div>
        <div className="form-row">
          <Field label="发件地址">
            <Input value={fromAddress} onChange={(event) => setFromAddress(event.target.value)} maxLength={200} />
          </Field>
          <Field label="收件地址" required>
            <Input value={toAddress} onChange={(event) => setToAddress(event.target.value)} maxLength={200}
              placeholder="接收提醒的邮箱" />
          </Field>
        </div>
        <label className="decision-radio" style={{ marginBottom: 16 }}>
          <input type="checkbox" checked={useStartTls} onChange={(event) => setUseStartTls(event.target.checked)} />
          使用 STARTTLS 加密
        </label>
        <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
          <Button variant="default" type="submit" disabled={updateChannel.isPending}>
            {updateChannel.isPending ? '保存中…' : '保存配置'}
          </Button>
          <Button variant="primary" type="button" disabled={testChannel.isPending} onClick={runTest}>
            {testChannel.isPending ? '发送中…' : '发送测试邮件'}
          </Button>
        </div>
        {testResult ? (
          <p style={{ margin: '12px 0 0' }}>
            <Badge variant={testResult.status === 'SENT' ? 'success' : testResult.status === 'PENDING' ? 'info' : 'danger'}>
              {testResult.status === 'SENT' ? '发送成功' : testResult.status === 'PENDING' ? '已入队待投递' : '发送失败'}
            </Badge>
            {testResult.failureReason ? (
              <span className="muted" style={{ marginLeft: 8 }}>{testResult.failureReason}</span>
            ) : null}
          </p>
        ) : null}
      </form>
    </div>
  )
}

function WebhookChannelCard({ channel }: { channel: ChannelData }) {
  const webhookConfig =
    channel.config && channel.config.channelType === 'WEBHOOK' ? channel.config : null
  const [url, setUrl] = useState(webhookConfig?.url ?? '')
  const [secret, setSecret] = useState('')
  const [providerType, setProviderType] = useState<'' | 'FEISHU' | 'DINGTALK' | 'WECOM'>(
    webhookConfig?.providerType ?? '',
  )
  const updateChannel = useUpdateNotificationChannel('WEBHOOK')
  const testChannel = useTestNotificationChannel('WEBHOOK')
  const [testResult, setTestResult] = useState<{ status: string; failureReason: string | null } | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const payload: WebhookChannelConfig = {
      channelType: 'WEBHOOK',
      url: url.trim(),
      // secret 留空表示保留既有凭据
      secret: secret ? secret : null,
      providerType: providerType || null,
    }
    try {
      await updateChannel.mutateAsync({ version: channel.version, body: { enabled: channel.enabled, config: payload } })
      setSecret('')
      pushToast('Webhook 渠道配置已保存')
    } catch (caught) {
      pushToast(isApiError(caught) || isNetworkError(caught) ? caught.message : '保存失败，请稍后重试', 'error')
    }
  }

  const runTest = async () => {
    setTestResult(null)
    try {
      const result = await testChannel.mutateAsync()
      const delivery = result.deliveries?.[0]
      if (!delivery) {
        setTestResult({ status: 'FAILED', failureReason: '未生成投递记录' })
        return
      }
      setTestResult({ status: delivery.status, failureReason: delivery.failureReason ?? null })
    } catch (caught) {
      const message = isApiError(caught) || isNetworkError(caught) ? caught.message : '测试失败，请稍后重试'
      setTestResult({ status: 'FAILED', failureReason: message })
    }
  }

  return (
    <div style={{ marginTop: 12 }}>
      <div className="requirement-row" style={{ flexWrap: 'wrap' }}>
        <div className="requirement-main">
          <span className="requirement-raw">Webhook</span>
          <span className="muted">
            到期提醒按已配置的 URL 发起 HTTP POST；secret 仅保存在本地数据库，不参与导出。站内通知始终保留兜底。
          </span>
        </div>
        <div className="requirement-actions">
          <Badge variant={channel.enabled ? 'success' : 'neutral'}>{channel.enabled ? '已启用' : '未启用'}</Badge>
          <Button
            size="sm"
            variant="ghost"
            type="button"
            disabled={updateChannel.isPending}
            onClick={async () => {
              try {
                await updateChannel.mutateAsync({ version: channel.version, body: { enabled: !channel.enabled } })
                pushToast(channel.enabled ? 'Webhook 渠道已停用' : 'Webhook 渠道已启用')
              } catch (caught) {
                pushToast(isApiError(caught) || isNetworkError(caught) ? caught.message : '保存失败，请稍后重试', 'error')
              }
            }}
          >
            {channel.enabled ? '停用' : '启用'}
          </Button>
        </div>
      </div>
      <form onSubmit={submit} noValidate style={{ marginTop: 12 }}>
        <div className="form-row">
          <Field label="Webhook URL" required>
            <Input value={url} onChange={(event) => setUrl(event.target.value)} maxLength={500}
              placeholder="例如 https://oapi.dingtalk.com/robot/send?access_token=..." />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Secret">
            <Input type="password" value={secret} onChange={(event) => setSecret(event.target.value)} maxLength={500}
              placeholder={channel.hasCredential ? '已保存（留空保持不变）' : '可选，作为 X-Webhook-Secret 头透传'}
              autoComplete="new-password" />
          </Field>
          <Field label="平台类型">
            <select
              className="input"
              value={providerType}
              onChange={(event) => setProviderType(event.target.value as '' | 'FEISHU' | 'DINGTALK' | 'WECOM')}
              style={{ width: '100%' }}
            >
              <option value="">通用（不指定）</option>
              <option value="FEISHU">飞书</option>
              <option value="DINGTALK">钉钉</option>
              <option value="WECOM">企业微信</option>
            </select>
          </Field>
        </div>
        <div className="flex-row" style={{ justifyContent: 'flex-start' }}>
          <Button variant="default" type="submit" disabled={updateChannel.isPending}>
            {updateChannel.isPending ? '保存中…' : '保存配置'}
          </Button>
          <Button variant="primary" type="button" disabled={testChannel.isPending} onClick={runTest}>
            {testChannel.isPending ? '发送中…' : '发送测试通知'}
          </Button>
        </div>
        {testResult ? (
          <p style={{ margin: '12px 0 0' }}>
            <Badge variant={testResult.status === 'SENT' ? 'success' : testResult.status === 'PENDING' ? 'info' : 'danger'}>
              {testResult.status === 'SENT' ? '发送成功' : testResult.status === 'PENDING' ? '已入队待投递' : '发送失败'}
            </Badge>
            {testResult.failureReason ? (
              <span className="muted" style={{ marginLeft: 8 }}>{testResult.failureReason}</span>
            ) : null}
          </p>
        ) : null}
      </form>
    </div>
  )
}

export function NotificationChannelSection() {
  const emailQuery = useNotificationChannel('EMAIL')
  const webhookQuery = useNotificationChannel('WEBHOOK')

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">通知渠道</h2>
      </div>
      <div className="card-body">
        <p className="muted" style={{ marginTop: 0 }}>
          提醒到期后除站内通知外，可按已启用渠道补充投递；各渠道独立记录发送状态与失败原因，
          权限被拒或发送失败时站内通知始终保留。
        </p>
        <BrowserChannelCard />
        {emailQuery.isLoading ? (
          <Spinner label="加载邮件渠道配置…" />
        ) : emailQuery.error || !emailQuery.data ? (
          <ErrorState error={emailQuery.error ?? new Error('加载失败')} onRetry={() => emailQuery.refetch()} />
        ) : (
          <EmailChannelCard channel={emailQuery.data} />
        )}
        {webhookQuery.isLoading ? (
          <Spinner label="加载 Webhook 渠道配置…" />
        ) : webhookQuery.error || !webhookQuery.data ? (
          <ErrorState error={webhookQuery.error ?? new Error('加载失败')} onRetry={() => webhookQuery.refetch()} />
        ) : (
          <WebhookChannelCard channel={webhookQuery.data} />
        )}
      </div>
    </section>
  )
}
