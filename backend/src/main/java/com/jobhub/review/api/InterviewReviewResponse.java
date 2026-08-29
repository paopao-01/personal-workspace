package com.jobhub.review.api;

import com.jobhub.interview.domain.InterviewResult;
import com.jobhub.review.domain.InterviewReview;
import com.jobhub.review.domain.ReviewStatus;
import java.util.List;

public record InterviewReviewResponse(
	String id,
	String interviewId,
	ReviewStatus status,
	InterviewResult interviewResult,
	boolean noQuestionsRecorded,
	String overallFeeling,
	String interviewerFocus,
	String jobInterest,
	String projectExpressRisk,
	long version,
	String lastModifiedAt,
	List<InterviewQuestionResponse> questions
) {
	public static InterviewReviewResponse from(InterviewReview review) {
		return new InterviewReviewResponse(
			review.getId(),
			review.getInterviewId(),
			review.getStatus(),
			review.getInterviewResult(),
			review.isNoQuestionsRecorded(),
			review.getOverallFeeling(),
			review.getInterviewerFocus(),
			review.getJobInterest(),
			review.getProjectExpressionRisk(),
			review.getVersion(),
			review.getUpdatedAt(),
			review.getQuestions().stream().map(InterviewQuestionResponse::from).toList()
		);
	}
}
