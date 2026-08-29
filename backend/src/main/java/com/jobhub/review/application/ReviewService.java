package com.jobhub.review.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.error.IllegalStateTransitionException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.datamanagement.application.TrashService;
import com.jobhub.interview.application.InterviewService;
import com.jobhub.interview.domain.Interview;
import com.jobhub.interview.domain.InterviewResult;
import com.jobhub.interview.domain.InterviewScheduleStatus;
import com.jobhub.review.domain.*;
import com.jobhub.review.infrastructure.AnalysisResultCountRow;
import com.jobhub.review.infrastructure.AnalysisStatusCountRow;
import com.jobhub.review.infrastructure.QuestionMapper;
import com.jobhub.review.infrastructure.ReviewMapper;
import com.jobhub.review.infrastructure.WeakKnowledgePointRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ReviewService {
	private final ReviewMapper reviewMapper;
	private final QuestionMapper questionMapper;
	private final InterviewService interviewService;
	private final TrashService trashService;
	private final IdGenerator ids;
	private final UtcTime time;

	public ReviewService(ReviewMapper reviewMapper, QuestionMapper questionMapper, InterviewService interviewService,
			TrashService trashService, IdGenerator ids, UtcTime time) {
		this.reviewMapper = reviewMapper;
		this.questionMapper = questionMapper;
		this.interviewService = interviewService;
		this.trashService = trashService;
		this.ids = ids;
		this.time = time;
	}

	public InterviewReview getByInterview(String interviewId) {
		interviewService.get(interviewId);
		return hydrate(requireReview(reviewMapper.selectByInterview(interviewId), "InterviewReview", interviewId));
	}

	@Transactional
	public InterviewReview saveDraft(String interviewId, Long expectedVersion, InterviewResult result,
			Boolean noQuestionsRecorded, String overallFeeling, String interviewerFocus, String jobInterest,
			String projectExpressionRisk) {
		Interview interview = interviewService.get(interviewId);
		if (interview.getScheduleStatus() != InterviewScheduleStatus.COMPLETED) {
			throw new BusinessRuleException("Review draft can only be saved after the interview is completed");
		}
		String now = time.now();
		InterviewReview existing = reviewMapper.selectByInterview(interviewId);
		if (existing == null) {
			InterviewReview created = InterviewReview.draft(ids.newId(), interviewId, result,
				Boolean.TRUE.equals(noQuestionsRecorded), blankToNull(overallFeeling), blankToNull(interviewerFocus),
				blankToNull(jobInterest), blankToNull(projectExpressionRisk), now);
			reviewMapper.insert(created);
			return hydrate(requireReview(reviewMapper.selectById(created.getId()), "InterviewReview", created.getId()));
		}
		if (expectedVersion == null) {
			throw new BusinessRuleException("If-Match-Version is required when updating an existing review");
		}
		if (existing.getStatus() == ReviewStatus.COMPLETED) {
			throw new BusinessRuleException("Completed review must be reopened before saving a draft");
		}
		existing.updateDraft(result, Boolean.TRUE.equals(noQuestionsRecorded), blankToNull(overallFeeling),
			blankToNull(interviewerFocus), blankToNull(jobInterest), blankToNull(projectExpressionRisk), now);
		VersionCheck.requireAffected(reviewMapper.updateDraft(existing, expectedVersion), existing.getVersion());
		return hydrate(requireReview(reviewMapper.selectById(existing.getId()), "InterviewReview", existing.getId()));
	}

	@Transactional
	public InterviewReview complete(String reviewId, long expectedVersion) {
		InterviewReview review = hydrate(requireReview(reviewMapper.selectById(reviewId), "InterviewReview", reviewId));
		if (review.getStatus() != ReviewStatus.DRAFT) {
			throw new BusinessRuleException("Only DRAFT review can be completed");
		}
		if (review.getInterviewResult() == null) {
			throw new BusinessRuleException("Interview result is required before completing review");
		}
		if (!review.isNoQuestionsRecorded() && review.getQuestions().isEmpty()) {
			throw new BusinessRuleException("Add at least one question or mark no questions recorded before completing review");
		}
		boolean hasQuestionWithoutAnswerStatus = review.getQuestions().stream().anyMatch(q -> q.getAnswerStatus() == null);
		if (hasQuestionWithoutAnswerStatus) {
			throw new BusinessRuleException("Every question must have answer status before completing review");
		}
		VersionCheck.requireAffected(reviewMapper.complete(reviewId, expectedVersion, time.now()), review.getVersion());
		return hydrate(requireReview(reviewMapper.selectById(reviewId), "InterviewReview", reviewId));
	}

	/**
	 * 重新打开已完成的复盘（COMPLETED -> DRAFT，状态机第 5 章）。
	 * 问题、知识点与学习任务来源关联全部保留，仅状态回退为 DRAFT。
	 */
	@Transactional
	public InterviewReview reopen(String reviewId, long expectedVersion) {
		InterviewReview review = hydrate(requireReview(reviewMapper.selectById(reviewId), "InterviewReview", reviewId));
		if (review.getStatus() != ReviewStatus.COMPLETED) {
			throw new IllegalStateTransitionException(review.getStatus().name(), ReviewStatus.DRAFT.name(),
				"only COMPLETED review can be reopened");
		}
		VersionCheck.requireAffected(reviewMapper.reopen(reviewId, expectedVersion, time.now()), review.getVersion());
		return hydrate(requireReview(reviewMapper.selectById(reviewId), "InterviewReview", reviewId));
	}

	@Transactional
	public InterviewQuestion addQuestion(String reviewId, String content, AnswerStatus answerStatus, String type,
			List<String> knowledgePointIds) {
		InterviewReview review = requireReview(reviewMapper.selectById(reviewId), "InterviewReview", reviewId);
		if (review.getStatus() == ReviewStatus.COMPLETED) {
			throw new BusinessRuleException("Completed review must be reopened before adding questions");
		}
		String now = time.now();
		InterviewQuestion question = InterviewQuestion.create(ids.newId(), reviewId, content.trim(), answerStatus,
			blankToNull(type), now);
		questionMapper.insert(question);
		if (knowledgePointIds != null) {
			for (String knowledgePointId : knowledgePointIds) {
				if (knowledgePointId != null && !knowledgePointId.isBlank()) {
					questionMapper.insertKnowledge(question.getId(), knowledgePointId, now);
				}
			}
		}
		reviewMapper.bumpVersion(reviewId, now);
		return hydrateQuestion(requireQuestion(questionMapper.selectById(question.getId()), question.getId()));
	}

	@Transactional
	public InterviewQuestion updateQuestion(String questionId, long expectedVersion, String content,
			AnswerStatus answerStatus, String type, List<String> knowledgePointIds, String myAnswer,
			String referenceAnswer, Integer difficulty, String errorReason, String improvementPlan) {
		InterviewQuestion question = requireQuestion(questionMapper.selectById(questionId), questionId);
		String now = time.now();
		question.update(content.trim(), answerStatus, blankToNull(type), blankToNull(myAnswer),
			blankToNull(referenceAnswer), difficulty, blankToNull(errorReason), blankToNull(improvementPlan), now);
		VersionCheck.requireAffected(questionMapper.updateQuestion(question, expectedVersion), question.getVersion());
		questionMapper.deleteKnowledgeForQuestion(questionId);
		if (knowledgePointIds != null) {
			for (String knowledgePointId : knowledgePointIds) {
				if (knowledgePointId != null && !knowledgePointId.isBlank()) {
					requireKnowledgePoint(knowledgePointId);
					questionMapper.insertKnowledge(questionId, knowledgePointId, now);
				}
			}
		}
		reviewMapper.bumpVersion(question.getReviewId(), now);
		return hydrateQuestion(requireQuestion(questionMapper.selectById(questionId), questionId));
	}

	@Transactional
	public void deleteQuestion(String questionId, long expectedVersion) {
		InterviewQuestion question = requireQuestion(questionMapper.selectById(questionId), questionId);
		long knowledgePointCount = questionMapper.selectKnowledgePoints(questionId).size();
		String now = time.now();
		VersionCheck.requireAffected(questionMapper.softDelete(questionId, expectedVersion, now), question.getVersion());
		trashService.recordDeletion(TrashService.TYPE_INTERVIEW_QUESTION, questionId, question.getContent(),
			List.of(knowledgePointCount + " 个知识点关联"), now);
	}

	public List<KnowledgePoint> listKnowledgePoints(String query) {
		return questionMapper.listKnowledgePoints(blankToNull(query));
	}

	@Transactional
	public KnowledgePoint createKnowledgePoint(String name, String category) {
		String normalizedName = normalizeName(name);
		KnowledgePoint existing = questionMapper.selectKnowledgePointByNormalizedName(normalizedName);
		if (existing != null) {
			return existing;
		}
		String id = ids.newId();
		questionMapper.insertKnowledgePoint(id, name.trim(), normalizedName, blankToNull(category), time.now());
		return requireKnowledgePoint(id);
	}

	public List<WeakKnowledgePoint> weakKnowledgePoints(String from, String to, String jobId) {
		String normalizedFrom = blankToNull(from);
		String normalizedTo = blankToNull(to);
		String normalizedJobId = blankToNull(jobId);
		return questionMapper.selectWeakKnowledgePoints(normalizedFrom, normalizedTo, normalizedJobId).stream()
			.map(row -> toWeakKnowledgePoint(row, normalizedFrom, normalizedTo, normalizedJobId))
			.toList();
	}

	/**
	 * 跨面试复盘聚合分析：按面试开始日期与岗位过滤，只汇总原始计数，
	 * 完全答出率以分子/分母片段输出（PRD 16.2），不做趋势推断。
	 */
	public ReviewAnalysis analysis(String from, String to, String jobId) {
		String normalizedFrom = blankToNull(from);
		String normalizedTo = blankToNull(to);
		String normalizedJobId = blankToNull(jobId);
		long totalCount = 0;
		long fullyAnsweredCount = 0;
		long partiallyAnsweredCount = 0;
		long unansweredCount = 0;
		for (AnalysisStatusCountRow row : questionMapper.selectAnalysisQuestionStatusCounts(normalizedFrom,
				normalizedTo, normalizedJobId)) {
			totalCount += row.getCnt();
			switch (row.getAnswerStatus()) {
				case "FULLY_ANSWERED" -> fullyAnsweredCount = row.getCnt();
				case "PARTIALLY_ANSWERED" -> partiallyAnsweredCount = row.getCnt();
				case "UNANSWERED" -> unansweredCount = row.getCnt();
				default -> { }
			}
		}
		List<ReviewAnalysis.KnowledgePointStat> knowledgePointStats = questionMapper
			.selectAnalysisKnowledgePointStats(normalizedFrom, normalizedTo, normalizedJobId)
			.stream()
			.map(row -> new ReviewAnalysis.KnowledgePointStat(
				new KnowledgePoint(row.getKnowledgePointId(), row.getName(), row.getCategory()),
				row.getQuestionCount(), row.getFullyAnsweredCount()))
			.toList();
		List<ReviewAnalysis.QuestionTypeStat> questionTypeStats = questionMapper
			.selectAnalysisQuestionTypeStats(normalizedFrom, normalizedTo, normalizedJobId)
			.stream()
			.map(row -> new ReviewAnalysis.QuestionTypeStat(row.getType(), row.getQuestionCount(),
				row.getFullyAnsweredCount()))
			.toList();
		long reviewCount = 0;
		long withResultCount = 0;
		long passedCount = 0;
		long failedCount = 0;
		long pendingCount = 0;
		for (AnalysisResultCountRow row : reviewMapper.selectAnalysisResultCounts(normalizedFrom, normalizedTo,
				normalizedJobId)) {
			reviewCount += row.getCnt();
			if (row.getInterviewResult() == null) {
				continue;
			}
			withResultCount += row.getCnt();
			switch (row.getInterviewResult()) {
				case "PASSED" -> passedCount = row.getCnt();
				case "FAILED" -> failedCount = row.getCnt();
				case "PENDING" -> pendingCount = row.getCnt();
				default -> { }
			}
		}
		return new ReviewAnalysis(normalizedFrom, normalizedTo, reviewCount, totalCount, fullyAnsweredCount,
			partiallyAnsweredCount, unansweredCount, knowledgePointStats, questionTypeStats, withResultCount,
			passedCount, failedCount, pendingCount);
	}

	private InterviewReview hydrate(InterviewReview review) {
		List<InterviewQuestion> questions = questionMapper.selectByReview(review.getId()).stream()
			.map(this::hydrateQuestion)
			.toList();
		review.setQuestions(questions);
		return review;
	}

	private InterviewQuestion hydrateQuestion(InterviewQuestion question) {
		question.setKnowledgePoints(questionMapper.selectKnowledgePoints(question.getId()));
		return question;
	}

	private WeakKnowledgePoint toWeakKnowledgePoint(WeakKnowledgePointRow row, String from, String to, String jobId) {
		KnowledgePoint knowledgePoint = new KnowledgePoint(row.getKnowledgePointId(), row.getName(), row.getCategory());
		List<InterviewQuestion> questions = questionMapper
			.selectWeakQuestions(row.getKnowledgePointId(), from, to, jobId)
			.stream()
			.map(this::hydrateQuestion)
			.toList();
		return new WeakKnowledgePoint(knowledgePoint, row.getWeightedWeaknessCount(), row.getQuestionCount(), questions);
	}

	private KnowledgePoint requireKnowledgePoint(String id) {
		KnowledgePoint knowledgePoint = questionMapper.selectKnowledgePointById(id);
		VersionCheck.requireFound(knowledgePoint, "KnowledgePoint", id);
		return knowledgePoint;
	}

	private InterviewReview requireReview(InterviewReview review, String type, String id) {
		VersionCheck.requireFound(review, type, id);
		return review;
	}

	private InterviewQuestion requireQuestion(InterviewQuestion question, String id) {
		VersionCheck.requireFound(question, "InterviewQuestion", id);
		return question;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String normalizeName(String value) {
		String normalized = blankToNull(value);
		if (normalized == null) {
			throw new BusinessRuleException("Knowledge point name is required");
		}
		return normalized.toLowerCase();
	}
}
