import { useState } from 'react'
import { isApiError, isNetworkError } from '@/api/errors'
import {
  useSkillProfiles,
} from '@/api/skills/useSkillQueries'
import { useUpdateSelfLevel } from '@/api/skills/useSkillMutations'
import type { SkillProfile } from '@/api/skills/skillApi'
import { pushToast } from '@/components/feedback/toastStore'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorState } from '@/components/ui/ErrorState'
import { Select } from '@/components/ui/Form'
import { Spinner } from '@/components/ui/Spinner'
import {
  evidenceStatusLabel,
  selfLevelLabel,
} from '@/features/skills/skillLabels'

const LEVEL_OPTIONS = [0, 1, 2, 3, 4, 5]

export function SkillsPage() {
  const skillsQuery = useSkillProfiles()
  const updateSelfLevel = useUpdateSelfLevel()
  const [error, setError] = useState<string | null>(null)
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
                      <Badge variant="subtle">面试表现：未评估</Badge>
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
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
