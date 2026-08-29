package com.jobhub.interview.infrastructure;

/**
 * 到期提醒扫描投影：提醒字段 + 面试轮次名（用于通知文案）。
 */
public class DueReminderRow {
	private String id;
	private String interviewId;
	private String reminderType;
	private String scheduledAt;
	private String roundName;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getInterviewId() { return interviewId; }
	public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
	public String getReminderType() { return reminderType; }
	public void setReminderType(String reminderType) { this.reminderType = reminderType; }
	public String getScheduledAt() { return scheduledAt; }
	public void setScheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; }
	public String getRoundName() { return roundName; }
	public void setRoundName(String roundName) { this.roundName = roundName; }
}
