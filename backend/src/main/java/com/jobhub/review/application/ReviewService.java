package com.jobhub.review.application;

import com.jobhub.common.error.BusinessRuleException;
import com.jobhub.common.id.IdGenerator;
import com.jobhub.common.time.UtcTime;
import com.jobhub.common.version.VersionCheck;
import com.jobhub.interview.application.InterviewService;
import com.jobhub.interview.domain.Interview;
import com.jobhub.interview.domain.InterviewResult;
import com.jobhub.interview.domain.InterviewScheduleStatus;
import com.jobhub.review.domain.*;
import com.jobhub.review.infrastructure.QuestionMapper;
import com.jobhub.review.infrastructure.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ReviewService {
	private final ReviewMapper reviewMapper;
	private final QuestionMapper questionMapper;
	private final InterviewService interviewService;
	private final IdGenerator ids;
	private final UtcTime time;

	public ReviewService(ReviewMapper reviewMapper, QuestionMapper questionMapper, InterviewService interviewService,
			IdGenerator ids, UtcTime time) {
		this.reviewMapper = reviewMapper;
		this.questionMapper = questionMapper;
		this.interviewService = interviewService;
		this.ids = ids;
		this.time = time;
	}

	public InterviewReview getByInterview(String interviewId) {
		interviewService.get(interviewId);
		return hydrate(requireReview(reviewMapper.selectByInterview(interviewId), "InterviewReview", interviewId));
	}

	@Transactional
	public InterviewReview saveDraft(String interviewId, Long expectedVersion, InterviewResult result,
			Boolean noQuestionsRecorded, String overallFeeling, String interviewerFocus, String jobInterest) {
		Interview interview = interviewService.get(interviewId);
		if (interview.getScheduleStatus() != InterviewScheduleStatus.COMPLETED) {
			throw new BusinessRuleException("Review draft can only be saved after the interview is completed");
		}
		String now = time.now();
		InterviewReview existing = reviewMapper.selectByInterview(interviewId);
		if (existing == null) {
			InterviewReview created = InterviewReview.draft(ids.newId(), interviewId, result,
				Boolean.TRUE.equals(noQuestionsRecorded), blankToNull(overallFeeling), blankToNull(interviewerFocus),
				blankToNull(jobInterest), now);
			reviewMapper.insert(created);
			return hydrate(requireReview(reviewMapper.selectById(created.getId()), "InterviewReview", created.getId()));
		}
		if (expectedVersion == null) {
			throw new BusinessRuleException("If-Match-Version is required when updating an existing review");
		}
		existing.updateDraft(result, Boolean.TRUE.equals(noQuestionsRecorded), blankToNull(overallFeeling),
			blankToNull(interviewerFocus), blankToNull(jobInterest), now);
		VersionCheck.requireAffected(reviewMapper.updateDraft(existing, expectedVersion), existing.getVersion());
		return hydrate(requireReview(reviewMapper.selectById(existing.getId()), "InterviewReview", existing.getId()));
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
}
