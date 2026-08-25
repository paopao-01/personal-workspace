package com.jobhub.job.domain;

/**
 * 要求当前匹配结论。每个 CONFIRMED 要求至多一条当前匹配记录。
 * 修改 JD 后此记录失效（被删除），保留人工修正原因前需 application 层在删除前快照（本切片简化为删除）。
 */
public class RequirementMatch {

	private String id;
	private String requirementId;
	private GapStatus matchStatus;
	private String evidenceSnapshotJson;
	private String manualOverrideReason;
	private String calculatedAt;
	private String updatedAt;
	private long version;

	public RequirementMatch() { }

	public static RequirementMatch initial(String id, String requirementId, GapStatus status, String now) {
		RequirementMatch m = new RequirementMatch();
		m.id = id;
		m.requirementId = requirementId;
		m.matchStatus = status;
		m.evidenceSnapshotJson = "[]";
		m.calculatedAt = now;
		m.updatedAt = now;
		m.version = 0;
		return m;
	}

	public RequirementMatch override(GapStatus newStatus, String reason, String now) {
		this.matchStatus = newStatus;
		this.manualOverrideReason = reason;
		this.updatedAt = now;
		return this;
	}

	public String getId() { return id; }
	public String getRequirementId() { return requirementId; }
	public GapStatus getMatchStatus() { return matchStatus; }
	public String getEvidenceSnapshotJson() { return evidenceSnapshotJson; }
	public String getManualOverrideReason() { return manualOverrideReason; }
	public String getCalculatedAt() { return calculatedAt; }
	public String getUpdatedAt() { return updatedAt; }
	public long getVersion() { return version; }
}
