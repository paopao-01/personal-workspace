package com.jobhub.job.domain;

/**
 * 岗位要求实体。可由规则提取（PENDING）或用户手动新增（CONFIRMED）。
 * 修改 JD 后所有未删除候选要求置为 PENDING（02-state-machines.md 2.2）。
 */
public class JobRequirement {

	private String id;
	private String jobId;
	private String rawText;
	private String normalizedName;
	private RequirementType type;
	private String proficiencyText;
	private ConfirmationStatus confirmationStatus;
	private RequirementSource source;
	private int sortOrder;
	private String mergedIntoRequirementId;
	private long version;
	private String createdAt;
	private String updatedAt;
	private String deletedAt;

	public JobRequirement() { }

	public static JobRequirement createFromRule(String id, String jobId, String rawText,
												String normalizedName, RequirementType type,
												String proficiencyText, int sortOrder, String now) {
		JobRequirement r = new JobRequirement();
		r.id = id;
		r.jobId = jobId;
		r.rawText = rawText;
		r.normalizedName = normalizedName;
		r.type = type;
		r.proficiencyText = proficiencyText;
		r.confirmationStatus = ConfirmationStatus.PENDING;
		r.source = RequirementSource.RULE;
		r.sortOrder = sortOrder;
		r.version = 0;
		r.createdAt = now;
		r.updatedAt = now;
		return r;
	}

	public static JobRequirement createFromAi(String id, String jobId, String rawText, String normalizedName,
											  RequirementType type, String proficiencyText, int sortOrder, String now) {
		JobRequirement r = new JobRequirement();
		r.id = id;
		r.jobId = jobId;
		r.rawText = rawText;
		r.normalizedName = normalizedName;
		r.type = type;
		r.proficiencyText = proficiencyText;
		r.confirmationStatus = ConfirmationStatus.PENDING;
		r.source = RequirementSource.AI;
		r.sortOrder = sortOrder;
		r.version = 0;
		r.createdAt = now;
		r.updatedAt = now;
		return r;
	}

	public JobRequirement updateDetails(String rawText, String normalizedName, RequirementType type,
			String proficiencyText, String now) {
		this.rawText = rawText;
		this.normalizedName = normalizedName;
		this.type = type;
		this.proficiencyText = proficiencyText;
		this.updatedAt = now;
		return this;
	}

	public JobRequirement confirm(String now) {
		this.confirmationStatus = ConfirmationStatus.CONFIRMED;
		this.updatedAt = now;
		return this;
	}

	public JobRequirement ignore(String now) {
		this.confirmationStatus = ConfirmationStatus.IGNORED;
		this.updatedAt = now;
		return this;
	}

	public JobRequirement restoreToPending(String now) {
		this.confirmationStatus = ConfirmationStatus.PENDING;
		this.updatedAt = now;
		return this;
	}

	public void markPending(String now) {
		this.confirmationStatus = ConfirmationStatus.PENDING;
		this.updatedAt = now;
	}

	// --- getters ---

	public String getId() { return id; }
	public String getJobId() { return jobId; }
	public String getRawText() { return rawText; }
	public String getNormalizedName() { return normalizedName; }
	public RequirementType getType() { return type; }
	public String getProficiencyText() { return proficiencyText; }
	public ConfirmationStatus getConfirmationStatus() { return confirmationStatus; }
	public RequirementSource getSource() { return source; }
	public int getSortOrder() { return sortOrder; }
	public String getMergedIntoRequirementId() { return mergedIntoRequirementId; }
	public long getVersion() { return version; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public String getDeletedAt() { return deletedAt; }

	public void setVersion(long version) { this.version = version; }
}
