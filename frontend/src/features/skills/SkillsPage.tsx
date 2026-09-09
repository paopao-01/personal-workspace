import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useSkillProfiles,
  useSelfLevelHistory,
} from '@/api/skills/useSkillQueries'
import { useCreateSkill, useUpdateSelfLevel } from '@/api/skills/useSkillMutations'
import type { SkillProfile } from '@/api/skills/skillApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Field, Input, Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import {
  evidenceStatusLabel,
  selfLevelLabel,
} from '@/features/skills/skillLabels'

const LEVEL_OPTIONS = [0, 1, 2, 3, 4, 5]

function SelfLevelHistorySection({ skill }: { skill: SkillProfile }) {
  const [open, setOpen] = useState(false)
  const historyQuery = useSelfLevelHistory(skill.skillId, open)

  if (!open) {
    return (
      <div className="requirement-actions" style={{ marginTop: 4 }}>
        <Button size="sm" variant="ghost" type="button" onClick={() => setOpen(true)}>
          查看自评历史
        </Button>
      </div>
    )
  }

  if (historyQuery.isLoading) {
    return (
      <div className="requirement-actions" style={{ marginTop: 4 }}>
        <Spinner label="加载自评历史…" />
        <Button size="sm" variant="ghost" type="button" onClick={() => setOpen(false)}>
          收起
        </Button>
      </div>
    )
  }

  const entries = historyQuery.data ?? []
  return (
    <div className="requirement-actions" style={{ marginTop: 4, flexDirection: 'column', alignItems: 'stretch', gap: 8 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span className="requirement-raw" style={{ fontSize: 13, fontWeight: 500 }}>
          自评历史
        </span>
        <Button size="sm" variant="ghost" type="button" onClick={() => setOpen(false)}>
          收起
        </Button>
      </div>
      {entries.length === 0 ? (
        <EmptyState icon="📜" text="暂无自评历史" />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <div className="requirement-meta" style={{ fontSize: 13 }}>
            等级轨迹：
            {entries.map((entry, index) => {
              const fromLabel =
                entry.fromLevel === null || entry.fromLevel === undefined
                  ? '—'
                  : selfLevelLabel(entry.fromLevel)
              const arrow = index === 0 ? '' : ' → '
              return (
                <span key={entry.id}>
                  {arrow}
                  {fromLabel} → {selfLevelLabel(entry.toLevel)}
                </span>
              )
            })}
          </div>
          <table className="meta-table" style={{ width: '100%', fontSize: 12 }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left' }}>时间</th>
                <th style={{ textAlign: 'left' }}>变化</th>
                <th style={{ textAlign: 'left' }}>理由</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => {
                const fromLabel =
                  entry.fromLevel === null || entry.fromLevel === undefined
                    ? '—'
                    : selfLevelLabel(entry.fromLevel)
                return (
                  <tr key={entry.id}>
                    <td>{formatUtc(entry.occurredAt)}</td>
                    <td>
                      {fromLabel} → {selfLevelLabel(entry.toLevel)}
                    </td>
                    <td>{entry.reason ?? '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function formatUtc(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return iso
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

export function SkillsPage() {
  const skillsQuery = useSkillProfiles()
  const updateSelfLevel = useUpdateSelfLevel()
  const createSkill = useCreateSkill()
  const [error, setError] = useState<string | null>(null)
  const [newSkillName, setNewSkillName] = useState('')
  // 每行独立的自评编辑草稿（仅自评维度，不影响证据状态与面试表现）
  const [drafts, setDrafts] = useState<Record<string, string>>({})

  if (skillsQuery.isLoading) {
    return <Spinner label="加载技能画像…" />
  }
  if (skillsQuery.error) {
    return <ErrorState error={skillsQuery.error} onRetry={() => skillsQuery.refetch()} />
  }

  const skills = skillsQuery.data ?? []

  const reportError = (caught: Error) => {
    const message =
      isApiError(caught) || isNetworkError(caught)
        ? caught.message
        : '操作失败，请稍后重试'
    setError(message)
    pushToast(message, 'error')
  }

  const saveSelfLevel = async (skill: SkillProfile) => {
    const draft = drafts[skill.skillId]
    if (draft === undefined || draft === '') return
    setError(null)
    try {
      await updateSelfLevel.mutateAsync({
        skillId: skill.skillId,
        version: skill.version ?? 0,
        body: { selfLevel: Number(draft) },
      })
      pushToast(`已更新「${skill.skillName}」的自评等级`)
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  const addSkill = async () => {
    const name = newSkillName.trim()
    if (!name) return
    setError(null)
    try {
      await createSkill.mutateAsync({ name })
      setNewSkillName('')
      pushToast(`已添加技能「${name}」`)
    } catch (caught) {
      reportError(caught as Error)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">能力与证据</h1>
          <p className="page-subtitle">
            技能画像的三个维度（自评等级、证据状态、面试表现）相互独立，修改其中一项不会覆盖其余两项。
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
          <h2 className="card-title">添加技能</h2>
        </div>
        <div className="card-body">
          <div className="flex-row" style={{ alignItems: 'flex-end' }}>
            <div style={{ minWidth: 260, flex: 1 }}>
              <Field label="技能名称" hint="可添加未出现在岗位要求中的技能。">
                <Input value={newSkillName} onChange={(event) => setNewSkillName(event.target.value)} maxLength={100} />
              </Field>
            </div>
            <Button type="button" variant="primary" disabled={createSkill.isPending || !newSkillName.trim()} onClick={addSkill}>
              {createSkill.isPending ? '添加中…' : '添加'}
            </Button>
          </div>
        </div>
      </section>

      <section className="card">
        <div className="card-header">
          <h2 className="card-title">技能画像</h2>
        </div>
        <div className="card-body">
          {skills.length === 0 ? (
            <EmptyState
              icon="🧭"
              text="暂无技能记录。技能会随岗位分析与证据关联逐步建立，首次自评后即可在此维护等级。"
            />
          ) : (
            <div>
              {skills.map((skill) => (
                <div className="requirement-row" key={skill.skillId}>
                  <div className="requirement-main">
                    <span className="requirement-raw">{skill.skillName}</span>
                    <div className="requirement-meta" style={{ marginTop: 4 }}>
                      <Badge variant={skill.selfLevel === null ? 'subtle' : 'primary'}>
                        自评：{selfLevelLabel(skill.selfLevel)}
                      </Badge>
                      <Badge variant={skill.evidenceStatus === 'VALID' ? 'success' : skill.evidenceStatus === 'WEAK' ? 'warning' : 'subtle'}>
                        证据：{evidenceStatusLabel(skill.evidenceStatus)}
                      </Badge>
                      <Badge variant="subtle">面试表现：{skill.interviewPerformance ? '已记录' : '未评估'}</Badge>
                    </div>
                  </div>
                  <div className="requirement-actions">
                    <div style={{ width: 120 }}>
                      <Select
                        value={drafts[skill.skillId] ?? ''}
                        onChange={(event) =>
                          setDrafts((prev) => ({ ...prev, [skill.skillId]: event.target.value }))
                        }
                        aria-label={`选择 ${skill.skillName} 的自评等级`}
                      >
                        <option value="">选择等级</option>
                        {LEVEL_OPTIONS.map((level) => (
                          <option key={level} value={level}>
                            {level}
                          </option>
                        ))}
                      </Select>
                    </div>
                    <Button
                      size="sm"
                      variant="primary"
                      type="button"
                      disabled={
                        updateSelfLevel.isPending ||
                        !drafts[skill.skillId] ||
                        Number(drafts[skill.skillId]) === skill.selfLevel
                      }
                      onClick={() => saveSelfLevel(skill)}
                    >
                      保存
                    </Button>
                  </div>
                  <SelfLevelHistorySection skill={skill} />
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
