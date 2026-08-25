package com.jobhub.job.api;

import com.jobhub.job.domain.JobDecisionStatus;
import jakarta.validation.constraints.Size;

public class JobUpdateRequest {

	@Size(max = 100)
	private String companyName;

	@Size(max = 150)
	private String title;

	@Size(min = 20, max = 50000)
	private String jdRawText;

	@Size(max = 100)
	private String source;

	@Size(max = 2048)
	private String sourceUrl;

	@Size(max = 100)
	private String location;

	@Size(max = 100)
	private String salaryRange;

	@Size(max = 5000)
	private String notes;

	private JobDecisionStatus decisionStatus;

	@Size(max = 1000)
	private String decisionReason;

	public String getCompanyName() { return companyName; }
	public void setCompanyName(String companyName) { this.companyName = companyName; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getJdRawText() { return jdRawText; }
	public void setJdRawText(String jdRawText) { this.jdRawText = jdRawText; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public String getSourceUrl() { return sourceUrl; }
	public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
	public String getLocation() { return location; }
	public void setLocation(String location) { this.location = location; }
	public String getSalaryRange() { return salaryRange; }
	public void setSalaryRange(String salaryRange) { this.salaryRange = salaryRange; }
	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }
	public JobDecisionStatus getDecisionStatus() { return decisionStatus; }
	public void setDecisionStatus(JobDecisionStatus decisionStatus) { this.decisionStatus = decisionStatus; }
	public String getDecisionReason() { return decisionReason; }
	public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
}
