package com.jobhub.job.domain;

import com.jobhub.common.error.IllegalStateTransitionException;

import java.util.Objects;

/**
 * 岗位聚合根。包含基础信息与投递决定。
 * 不保存投递当前状态（投递进度由 application_record 维护）。
 *
 * 状态机（02-state-machines.md 2.1）：
 *   ACTIVE --archive--> ARCHIVED
 *   ARCHIVED --restore--> ACTIVE
 *
 * JD 修改（02-state-machines.md 2.2）：调用 updateJdRawText 标记 jd 脏标志，
 * application 层负责把候选要求置为 PENDING 并清空当前 requirement_match 结论。
 */
public class Job {

	private String id;
	private String companyName;
	private String title;
	private String jdRawText;
	private String source;
	private String sourceUrl;
	private String location;
	private String salaryRange;
	private JobDecisionStatus decisionStatus;  // nullable
	private String decisionReason;
	private JobStatus status;
	private String notes;
	private long version;
	private String createdAt;
	private String updatedAt;
	private String deletedAt;

	public Job() { }

	public static Job create(String id, String companyName, String title, String jdRawText,
							 String source, String sourceUrl, String location, String salaryRange,
							 String notes, String now) {
		Job job = new Job();
		job.id = id;
		job.companyName = companyName;
		job.title = title;
		job.jdRawText = jdRawText;
		job.source = source;
		job.sourceUrl = sourceUrl;
		job.location = location;
		job.salaryRange = salaryRange;
		job.notes = notes;
		job.status = JobStatus.ACTIVE;
		job.version = 0;
		job.createdAt = now;
		job.updatedAt = now;
		return job;
	}

	public Job archive(String now) {
		if (this.status != JobStatus.ACTIVE) {
			throw new IllegalStateTransitionException(this.status.name(), JobStatus.ARCHIVED.name(),
					"only ACTIVE job can be archived");
		}
		this.status = JobStatus.ARCHIVED;
		this.updatedAt = now;
		return this;
	}

	public Job restore(String now) {
		if (this.status != JobStatus.ARCHIVED) {
			throw new IllegalStateTransitionException(this.status.name(), JobStatus.ACTIVE.name(),
					"only ARCHIVED job can be restored");
		}
		this.status = JobStatus.ACTIVE;
		this.updatedAt = now;
		return this;
	}

	public boolean jdChanged(String newJdRawText) {
		return !Objects.equals(this.jdRawText, newJdRawText);
	}

	public Job updateBasicInfo(String companyName, String title, String jdRawText, String source,
							   String sourceUrl, String location, String salaryRange, String notes,
							   String now) {
		this.companyName = companyName;
		this.title = title;
		this.jdRawText = jdRawText;
		this.source = source;
		this.sourceUrl = sourceUrl;
		this.location = location;
		this.salaryRange = salaryRange;
		this.notes = notes;
		this.updatedAt = now;
		return this;
	}

	public Job updateDecision(JobDecisionStatus decisionStatus, String decisionReason, String now) {
		this.decisionStatus = decisionStatus;
		this.decisionReason = decisionReason;
		this.updatedAt = now;
		return this;
	}

	// --- getters ---

	public String getId() { return id; }
	public String getCompanyName() { return companyName; }
	public String getTitle() { return title; }
	public String getJdRawText() { return jdRawText; }
	public String getSource() { return source; }
	public String getSourceUrl() { return sourceUrl; }
	public String getLocation() { return location; }
	public String getSalaryRange() { return salaryRange; }
	public JobDecisionStatus getDecisionStatus() { return decisionStatus; }
	public String getDecisionReason() { return decisionReason; }
	public JobStatus getStatus() { return status; }
	public String getNotes() { return notes; }
	public long getVersion() { return version; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public String getDeletedAt() { return deletedAt; }

	public void setVersion(long version) { this.version = version; }
	public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
