package com.jobhub.review.domain;

import com.jobhub.interview.domain.InterviewResult;
import java.util.List;

public class InterviewReview {
	private String id;
	private String interviewId;
	private ReviewStatus status;
	private InterviewResult interviewResult;
	private boolean noQuestionsRecorded;
	private String overallFeeling;
	private String interviewerFocus;
	private String jobInterest;
	private String createdAt;
	private String updatedAt;
	private long version;
	private List<InterviewQuestion> questions = List.of();

	public static InterviewReview draft(String id, String interviewId, InterviewResult result, boolean noQuestionsRecorded,
			String overallFeeling, String interviewerFocus, String jobInterest, String now) {
		InterviewReview r = new InterviewReview();
		r.id = id;
		r.interviewId = interviewId;
		r.status = ReviewStatus.DRAFT;
		r.interviewResult = result;
		r.noQuestionsRecorded = noQuestionsRecorded;
		r.overallFeeling = overallFeeling;
		r.interviewerFocus = interviewerFocus;
		r.jobInterest = jobInterest;
		r.createdAt = now;
		r.updatedAt = now;
		return r;
	}

	public void updateDraft(InterviewResult result, boolean noQuestionsRecorded, String overallFeeling,
			String interviewerFocus, String jobInterest, String now) {
		this.status = ReviewStatus.DRAFT;
		this.interviewResult = result;
		this.noQuestionsRecorded = noQuestionsRecorded;
		this.overallFeeling = overallFeeling;
		this.interviewerFocus = interviewerFocus;
		this.jobInterest = jobInterest;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public String getInterviewId() { return interviewId; }
	public ReviewStatus getStatus() { return status; }
	public InterviewResult getInterviewResult() { return interviewResult; }
	public boolean isNoQuestionsRecorded() { return noQuestionsRecorded; }
	public String getOverallFeeling() { return overallFeeling; }
	public String getInterviewerFocus() { return interviewerFocus; }
	public String getJobInterest() { return jobInterest; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
	public List<InterviewQuestion> getQuestions() { return questions == null ? List.of() : questions; }
	public void setQuestions(List<InterviewQuestion> questions) { this.questions = questions == null ? List.of() : questions; }
}
