import { useState, type FormEvent } from 'react'
import {
  useActivateAiProvider,
  useAiProviders,
  useCreateAiProvider,
  useTestAiProvider,
  useUpdateAiProvider,
} from '@/api/ai/useAiQueries'
import { isApiError, isNetworkError } from '@/api/errors'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import type { AiProvider } from '@/api/ai/aiApi'

function errorMessage(caught: unknown) {
  return isApiError(caught) || isNetworkError(caught) ? (caught as Error).message : '操作失败，请稍后重试'
}

function ProviderRow({ provider }: { provider: AiProvider }) {
  const activate = useActivateAiProvider()
  const update = useUpdateAiProvider()
  const test = useTestAiProvider()
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string | null; latencyMs: number | null } | null>(null)
  const [editing, setEditing] = useState(false)
  const [model, setModel] = useState(provider.model)
  const [apiKey, setApiKey] = useState('')

  const runActivate = async () => {
    try {
      await activate.mutateAsync(provider.id)
      pushToast(`已切换到「${provider.name}」，新任务将使用该供应商`)
    } catch (caught) {
      pushToast(errorMessage(caught), 'error')
    }
  }

  const runTest = async () => {
    setTestResult(null)
    try {
      const result = await test.mutateAsync(provider.id)
      setTestResult({ ok: result.ok, latencyMs: result.latencyMs ?? null, message: result.message ?? null })
    } catch (caught) {
      setTestResult({ ok: false, message: errorMessage(caught), latencyMs: null })
    }
  }

  const saveEdit = async (event: FormEvent) => {
    event.preventDefault()
    try {
      await update.mutateAsync({
        providerId: provider.id,
        version: provider.version,
        body: {
          providerType: provider.providerType as 'OPENAI_COMPATIBLE' | 'ANTHROPIC',
          name: provider.name,
          baseUrl: provider.baseUrl,
          model: model.trim(),
          apiKey: apiKey ? apiKey : null,
        },
      })
      setEditing(false)
      setApiKey('')
      pushToast('供应商配置已保存')
    } catch (caught) {
      pushToast(errorMessage(caught), 'error')
    }
  }

  return (
    <div className="requirement-row" style={{ flexWrap: 'wrap' }}>
      <div className="requirement-main">
        <span className="requirement-raw">{provider.name}</span>
        <span className="muted">
          {provider.providerType === 'ANTHROPIC' ? 'Anthropic' : 'OpenAI 兼容'} · {provider.model} ·{' '}
          {provider.baseUrl}
        </span>
        {testResult ? (
          <p className="muted" style={{ margin: '4px 0 0' }}>
            <Badge variant={testResult.ok ? 'success' : 'danger'}>
              {testResult.ok ? `连通（${testResult.latencyMs}ms）` : '连通失败'}
            </Badge>
            {testResult.message ? <span style={{ marginLeft: 8 }}>{testResult.message}</span> : null}
          </p>
        ) : null}
      </div>
      <div className="requirement-actions">
        {provider.isActive ? (
          <Badge variant="success">激活中</Badge>
        ) : (
          <Button size="sm" variant="ghost" type="button" disabled={activate.isPending} onClick={runActivate}>
            切换为此供应商
          </Button>
        )}
        <Button size="sm" variant="default" type="button" disabled={test.isPending} onClick={runTest}>
          {test.isPending ? '测试中…' : '测试连通'}
        </Button>
        <Button size="sm" variant="ghost" type="button" onClick={() => setEditing((value) => !value)}>
          {editing ? '收起' : '编辑'}
        </Button>
      </div>
      {editing ? (
        <form onSubmit={saveEdit} className="inline-edit" style={{ width: '100%' }}>
          <div className="form-row">
            <Field label="模型">
              <Input value={model} onChange={(event) => setModel(event.target.value)} maxLength={200} />
            </Field>
            <Field label="API Key">
              <Input
                type="password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                maxLength={500}
                placeholder={provider.hasCredential ? '已保存（留空保持不变）' : '未设置'}
                autoComplete="new-password"
              />
            </Field>
          </div>
          <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
            <Button variant="default" type="submit" disabled={update.isPending}>
              {update.isPending ? '保存中…' : '保存修改'}
            </Button>
          </div>
        </form>
      ) : null}
    </div>
  )
}

export function AiProviderSection() {
  const providersQuery = useAiProviders()
  const create = useCreateAiProvider()
  const [showCreate, setShowCreate] = useState(false)
  const [providerType, setProviderType] = useState<'OPENAI_COMPATIBLE' | 'ANTHROPIC'>('OPENAI_COMPATIBLE')
  const [name, setName] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [model, setModel] = useState('')
  const [apiKey, setApiKey] = useState('')

  if (providersQuery.isLoading) return <Spinner label="加载 AI 供应商配置…" />
  if (providersQuery.error || !providersQuery.data) {
    return <ErrorState error={providersQuery.error ?? new Error('加载失败')} onRetry={() => providersQuery.refetch()} />
  }
  const providers = providersQuery.data

  const submitCreate = async (event: FormEvent) => {
    event.preventDefault()
    try {
      await create.mutateAsync({
        providerType,
        name: name.trim(),
        baseUrl: baseUrl.trim(),
        model: model.trim(),
        apiKey: apiKey ? apiKey : null,
      })
      setName('')
      setBaseUrl('')
      setModel('')
      setApiKey('')
      setShowCreate(false)
      pushToast('AI 供应商已添加')
    } catch (caught) {
      pushToast(errorMessage(caught), 'error')
    }
  }

  return (
    <section className="card">
      <div className="card-header">
        <h2 className="card-title">AI 供应商</h2>
        <Button size="sm" variant="ghost" type="button" onClick={() => setShowCreate((value) => !value)}>
          {showCreate ? '收起新增' : '新增供应商'}
        </Button>
      </div>
      <div className="card-body">
        <p className="muted" style={{ marginTop: 0 }}>
          可配置多个供应商并随时切换激活；新任务使用激活供应商。API Key 仅保存在本地数据库，不参与导出、不回显。
        </p>
        {providers.length === 0 ? (
          <p className="muted">尚未配置供应商。</p>
        ) : (
          providers.map((provider) => <ProviderRow key={provider.id} provider={provider} />)
        )}
        {showCreate ? (
          <form onSubmit={submitCreate} className="inline-edit" style={{ width: '100%' }}>
            <div className="form-row">
              <Field label="供应商类型" required>
                <Select
                  value={providerType}
                  onChange={(event) =>
                    setProviderType(event.target.value as 'OPENAI_COMPATIBLE' | 'ANTHROPIC')
                  }
                >
                  <option value="OPENAI_COMPATIBLE">OpenAI 兼容（DeepSeek/Kimi/GLM/Ollama 等）</option>
                  <option value="ANTHROPIC">Anthropic</option>
                </Select>
              </Field>
              <Field label="名称" required>
                <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={100} />
              </Field>
            </div>
            <div className="form-row">
              <Field label="Base URL" required>
                <Input
                  value={baseUrl}
                  onChange={(event) => setBaseUrl(event.target.value)}
                  maxLength={500}
                  placeholder="例如 https://api.deepseek.com/v1"
                />
              </Field>
              <Field label="模型" required>
                <Input value={model} onChange={(event) => setModel(event.target.value)} maxLength={200} />
              </Field>
            </div>
            <Field label="API Key">
              <Input
                type="password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                maxLength={500}
                autoComplete="new-password"
              />
            </Field>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              <Button variant="default" type="button" onClick={() => setShowCreate(false)}>
                取消
              </Button>
              <Button variant="primary" type="submit" disabled={create.isPending}>
                {create.isPending ? '保存中…' : '保存'}
              </Button>
            </div>
          </form>
        ) : null}
      </div>
    </section>
  )
}
