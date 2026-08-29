package com.jobhub.review.api;

import com.jobhub.interview.domain.InterviewResult;

public record ReviewUpsertRequest(
	InterviewResult interviewResult,
	Boolean noQuestionsRecorded,
	String overallFeeling,
	String interviewerFocus,
	String jobInterest,
	String projectExpressRisk
) { }
