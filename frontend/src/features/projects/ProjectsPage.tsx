import { useState, type FormEvent } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useCreateEvidence,
  useCreateProject,
  useUpdateEvidence,
  useUpdateProject,
} from '@/api/projects/useProjectMutations'
import { useEvidence, useProjects } from '@/api/projects/useProjectQueries'
import type {
  Evidence,
  EvidenceType,
  ProjectCaseSummary,
} from '@/api/projects/projectApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select, Textarea } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import { evidenceTypeLabels, evidenceTypeOptions } from '@/features/projects/projectLabels'

const URL_OR_PATH_HINT = '证据以外部链接或本地路径引用为主；应用不会自动读取、扫描或上传被引用的文件，路径仅作为文本保存。'

export function ProjectsPage() {
  const projectsQuery = useProjects()
  const evidenceQuery = useEvidence()

  // 项目案例表单
  const [projectTitle, setProjectTitle] = useState('')
  const [projectScenario, setProjectScenario] = useState('')
  const [projectApproach, setProjectApproach] = useState('')
  const [projectProblemSolved, setProjectProblemSolved] = useState('')
  const [projectResult, setProjectResult] = useState('')
  const [projectEvidenceIds, setProjectEvidenceIds] = useState<string[]>([])
  const [editingProject, setEditingProject] = useState<ProjectCaseSummary | null>(null)

  // 证据表单
  const [evidenceType, setEvidenceType] = useState<EvidenceType>('PROJECT_CODE')
  const [evidenceTitle, setEvidenceTitle] = useState('')
  const [evidenceWhereUsed, setEvidenceWhereUsed] = useState('')
  const [evidenceProblemSolved, setEvidenceProblemSolved] = useState('')
  const [evidenceApproach, setEvidenceApproach] = useState('')
  const [evidenceResult, setEvidenceResult] = useState('')
  const [evidenceUrlOrPath, setEvidenceUrlOrPath] = useState('')
  const [editingEvidence, setEditingEvidence] = useState<Evidence | null>(null)

  const [error, setError] = useState<string | null>(null)
  const createProject = useCreateProject()
  const updateProject = useUpdateProject()
  const createEvidence = useCreateEvidence()
  const updateEvidence = useUpdateEvidence()

  if (projectsQuery.isLoading || evidenceQuery.isLoading) {
    return <Spinner label="加载项目与证据…" />
  }
  if (projectsQuery.error) {
    return <ErrorState error={projectsQuery.error} onRetry={() => projectsQuery.refetch()} />
  }
  if (evidenceQuery.error) {
    return <ErrorState error={evidenceQuery.error} onRetry={() => evidenceQuery.refetch()} />
  }

  const projects = projectsQuery.data ?? []
  const evidence = evidenceQuery.data ?? []
  const pending =
    createProject.isPending || updateProject.isPending || createEvidence.isPending || updateEvidence.isPending

  const reportError = (caught: Error) => {
    const message =
      isApiError(caught) || isNetworkError(caught)
        ? caught.message
        : '操作失败，请稍后重试'
    setError(message)
    pushToast(message, 'error')
  }

  const resetProjectForm = () => {
    setEditingProject(null)
    setProjectTitle('')
    setProjectScenario('')
    setProjectApproach('')
    setProjectProblemSolved('')
    setProjectResult('')
    setProjectEvidenceIds([])
  }

  const resetEvidenceForm = () => {
    setEditingEvidence(null)
    setEvidenceType('PROJECT_CODE')
    setEvidenceTitle('')
    setEvidenceWhereUsed('')
    setEvidenceProblemSolved('')
    setEvidenceApproach('')
    setEvidenceResult('')
    setEvidenceUrlOrPath('')
  }

  const toggleEvidenceId = (evidenceId: string) => {
    setProjectEvidenceIds((prev) =>
      prev.includes(evidenceId)
        ? prev.filter((id) => id !== evidenceId)
        : [...prev, evidenceId],
    )
  }

  const submitProject = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const body = {
      title: projectTitle,
      scenario: projectScenario,
      approach: projectApproach,
      problemSolved: projectProblemSolved,
      result: projectResult.trim() || undefined,
      evidenceIds: projectEvidenceIds,
    }
    try {
      if (editingProject) {
        await updateProject.mutateAsync({
          projectId: editingProject.id,
          version: editingProject.version,
          body,
        })
        pushToast('项目案例已更新')
      } else {
        await createProject.mutateAsync(body)
        pushToast('项目案例已创建')
      }
      resetProjectForm()
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const submitEvidence = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const body = {
      type: evidenceType,
      title: evidenceTitle,
      whereUsed: evidenceWhereUsed.trim() || undefined,
      problemSolved: evidenceProblemSolved.trim() || undefined,
      approach: evidenceApproach.trim() || undefined,
      result: evidenceResult.trim() || undefined,
      urlOrPath: evidenceUrlOrPath.trim() || undefined,
    }
    try {
      if (editingEvidence) {
        await updateEvidence.mutateAsync({
          evidenceId: editingEvidence.id,
          version: editingEvidence.version,
          body,
        })
        pushToast('证据引用已更新')
      } else {
        await createEvidence.mutateAsync(body)
        pushToast('证据引用已创建')
      }
      resetEvidenceForm()
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const startEditProject = (project: ProjectCaseSummary) => {
    setEditingEvidence(null)
    resetEvidenceForm()
    setEditingProject(project)
    setProjectTitle(project.title)
    setProjectScenario(project.scenario)
    setProjectApproach(project.approach)
    setProjectProblemSolved(project.problemSolved)
    setProjectResult(project.result ?? '')
    setProjectEvidenceIds((project.evidenceRefs ?? []).map((ref) => ref.id))
  }

  const startEditEvidence = (item: Evidence) => {
    setEditingProject(null)
    resetProjectForm()
    setEditingEvidence(item)
    setEvidenceType(item.type)
    setEvidenceTitle(item.title)
    setEvidenceWhereUsed(item.whereUsed ?? '')
    setEvidenceProblemSolved(item.problemSolved ?? '')
    setEvidenceApproach(item.approach ?? '')
    setEvidenceResult(item.result ?? '')
    setEvidenceUrlOrPath(item.urlOrPath ?? '')
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">项目与证据</h1>
          <p className="page-subtitle">
            以最小成本补充可复用的项目表达和能力依据；面试准备包只展示这里维护的真实资料。
          </p>
        </div>
      </div>

      {error ? (
        <div className="conflict-banner">
          <span>{error}</span>
        </div>
      ) : null}

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">{editingProject ? '编辑项目案例' : '新建项目案例'}</h2>
        </div>
        <div className="card-body">
          <form onSubmit={submitProject} noValidate>
            <Field label="项目名称" required>
              <Input
                value={projectTitle}
                onChange={(event) => setProjectTitle(event.target.value)}
                maxLength={200}
                required
              />
            </Field>
            <div className="form-row">
              <Field label="使用场景" required>
                <Textarea
                  value={projectScenario}
                  onChange={(event) => setProjectScenario(event.target.value)}
                  rows={3}
                  maxLength={3000}
                  required
                />
              </Field>
              <Field label="采取方案" required>
                <Textarea
                  value={projectApproach}
                  onChange={(event) => setProjectApproach(event.target.value)}
                  rows={3}
                  maxLength={3000}
                  required
                />
              </Field>
            </div>
            <div className="form-row">
              <Field label="解决问题" required>
                <Textarea
                  value={projectProblemSolved}
                  onChange={(event) => setProjectProblemSolved(event.target.value)}
                  rows={3}
                  maxLength={3000}
                  required
                />
              </Field>
              <Field label="结果（可后补）">
                <Textarea
                  value={projectResult}
                  onChange={(event) => setProjectResult(event.target.value)}
                  rows={3}
                  maxLength={2000}
                  placeholder="没有数据时留空即可，不要编造量化指标"
                />
              </Field>
            </div>
            <Field label="关联证据">
              {evidence.length === 0 ? (
                <p className="form-hint">还没有证据引用，可先在下方创建，再回到这里关联。</p>
              ) : (
                <div className="evidence-picker" role="group" aria-label="选择关联证据">
                  {evidence.map((item) => (
                    <label key={item.id} className="evidence-picker-item">
                      <input
                        type="checkbox"
                        checked={projectEvidenceIds.includes(item.id)}
                        onChange={() => toggleEvidenceId(item.id)}
                      />
                      <span>
                        {item.title}
                        <span className="muted">（{evidenceTypeLabels[item.type]}）</span>
                      </span>
                    </label>
                  ))}
                </div>
              )}
            </Field>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              {editingProject ? (
                <Button variant="default" type="button" onClick={resetProjectForm}>
                  取消编辑
                </Button>
              ) : null}
              <Button
                variant="primary"
                type="submit"
                disabled={
                  pending ||
                  !projectTitle.trim() ||
                  !projectScenario.trim() ||
                  !projectApproach.trim() ||
                  !projectProblemSolved.trim()
                }
              >
                {editingProject ? '保存修改' : '创建项目案例'}
              </Button>
            </div>
          </form>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">项目案例列表</h2>
        </div>
        <div className="card-body">
          {projects.length === 0 ? (
            <EmptyState
              icon="🗂"
              text="暂无项目案例。补充一个可讲的项目，面试准备包就能展示“场景—方案—问题”的真实摘要。"
            />
          ) : (
            <div>
              {projects.map((project) => (
                <div className="requirement-row" key={project.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{project.title}</span>
                    <p className="muted" style={{ margin: '4px 0 0' }}>
                      场景：{project.scenario}
                    </p>
                    <p className="muted" style={{ margin: 0 }}>
                      方案：{project.approach}
                    </p>
                    <p className="muted" style={{ margin: 0 }}>
                      解决问题：{project.problemSolved}
                    </p>
                    {project.result ? (
                      <p className="muted" style={{ margin: 0 }}>
                        结果：{project.result}
                      </p>
                    ) : null}
                    {(project.evidenceRefs ?? []).length > 0 ? (
                      <p className="muted" style={{ margin: 0 }}>
                        证据引用：
                        {(project.evidenceRefs ?? []).map((ref) => ref.title).join('、')}
                      </p>
                    ) : null}
                  </div>
                  <div className="requirement-actions">
                    <Button
                      size="sm"
                      variant="default"
                      type="button"
                      onClick={() => startEditProject(project)}
                    >
                      编辑
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">{editingEvidence ? '编辑证据引用' : '新建证据引用'}</h2>
        </div>
        <div className="card-body">
          <form onSubmit={submitEvidence} noValidate>
            <div className="form-row">
              <Field label="证据类型" required>
                <Select
                  value={evidenceType}
                  onChange={(event) => setEvidenceType(event.target.value as EvidenceType)}
                  aria-label="证据类型"
                >
                  {evidenceTypeOptions.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="证据名称" required>
                <Input
                  value={evidenceTitle}
                  onChange={(event) => setEvidenceTitle(event.target.value)}
                  maxLength={200}
                  required
                />
              </Field>
            </div>
            <div className="form-row">
              <Field label="在哪里使用">
                <Textarea
                  value={evidenceWhereUsed}
                  onChange={(event) => setEvidenceWhereUsed(event.target.value)}
                  rows={2}
                  maxLength={2000}
                />
              </Field>
              <Field label="解决什么问题">
                <Textarea
                  value={evidenceProblemSolved}
                  onChange={(event) => setEvidenceProblemSolved(event.target.value)}
                  rows={2}
                  maxLength={2000}
                />
              </Field>
            </div>
            <div className="form-row">
              <Field label="采用什么方案">
                <Textarea
                  value={evidenceApproach}
                  onChange={(event) => setEvidenceApproach(event.target.value)}
                  rows={2}
                  maxLength={3000}
                />
              </Field>
              <Field label="结果如何（可后补）">
                <Textarea
                  value={evidenceResult}
                  onChange={(event) => setEvidenceResult(event.target.value)}
                  rows={2}
                  maxLength={2000}
                />
              </Field>
            </div>
            <Field label="链接或本地路径">
              <Input
                value={evidenceUrlOrPath}
                onChange={(event) => setEvidenceUrlOrPath(event.target.value)}
                maxLength={2000}
                placeholder="https://… 或 D:\docs\design.md"
              />
              <p className="form-hint">{URL_OR_PATH_HINT}</p>
            </Field>
            <div className="flex-row" style={{ justifyContent: 'flex-end' }}>
              {editingEvidence ? (
                <Button variant="default" type="button" onClick={resetEvidenceForm}>
                  取消编辑
                </Button>
              ) : null}
              <Button
                variant="primary"
                type="submit"
                disabled={pending || !evidenceTitle.trim()}
              >
                {editingEvidence ? '保存修改' : '创建证据引用'}
              </Button>
            </div>
          </form>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">证据引用列表</h2>
        </div>
        <div className="card-body">
          {evidence.length === 0 ? (
            <EmptyState
              icon="📎"
              text="暂无证据引用。可以先记录外部链接或本地路径，应用只保存文本引用，不会读取文件内容。"
            />
          ) : (
            <div>
              {evidence.map((item) => (
                <div className="requirement-row" key={item.id}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{item.title}</span>
                    <p className="muted" style={{ margin: '4px 0 0' }}>
                      类型：{evidenceTypeLabels[item.type]}
                    </p>
                    {item.whereUsed ? (
                      <p className="muted" style={{ margin: 0 }}>
                        在哪里使用：{item.whereUsed}
                      </p>
                    ) : null}
                    {item.urlOrPath ? (
                      <p className="muted" style={{ margin: 0 }}>
                        引用（仅文本）：{item.urlOrPath}
                      </p>
                    ) : null}
                  </div>
                  <div className="requirement-actions">
                    <Button
                      size="sm"
                      variant="default"
                      type="button"
                      onClick={() => startEditEvidence(item)}
                    >
                      编辑
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
