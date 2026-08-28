import type { AnswerStatus, InterviewReview } from '@/api/reviews/reviewApi'

export const reviewStatusLabel: Record<InterviewReview['status'], string> = {
  NOT_STARTED: '未开始',
  DRAFT: '草稿',
  COMPLETED: '已完成',
}

export const answerStatusLabel: Record<AnswerStatus, string> = {
  FULLY_ANSWERED: '完全答出',
  PARTIALLY_ANSWERED: '部分答出',
  UNANSWERED: '未答出',
}
