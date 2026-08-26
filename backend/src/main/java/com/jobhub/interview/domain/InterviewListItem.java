package com.jobhub.interview.domain;

import com.jobhub.application.domain.ApplicationStatus;

/**
 * 面试中心列表的只读投影，包含关联投递和岗位的必要上下文。
 */
public class InterviewListItem {

	private String id;
	private String applicationId;
	private String roundName;
	private String startsAt;
	private String eventTimeZone;
	private InterviewMode mode;
	private String meetingUrlOrAddress;
	private String contact;
	private InterviewScheduleStatus scheduleStatus;
	private InterviewResult result;
	private String notes;
	private long version;
	private ApplicationStatus applicationStatus;
	private String jobId;
	private String companyName;
	private String jobTitle;

	public String getId() { return id; }
	public String getApplicationId() { return applicationId; }
	public String getRoundName() { return roundName; }
	public String getStartsAt() { return startsAt; }
	public String getEventTimeZone() { return eventTimeZone; }
	public InterviewMode getMode() { return mode; }
	public String getMeetingUrlOrAddress() { return meetingUrlOrAddress; }
	public String getContact() { return contact; }
	public InterviewScheduleStatus getScheduleStatus() { return scheduleStatus; }
	public InterviewResult getResult() { return result; }
	public String getNotes() { return notes; }
	public long getVersion() { return version; }
	public ApplicationStatus getApplicationStatus() { return applicationStatus; }
	public String getJobId() { return jobId; }
	public String getCompanyName() { return companyName; }
	public String getJobTitle() { return jobTitle; }
}
