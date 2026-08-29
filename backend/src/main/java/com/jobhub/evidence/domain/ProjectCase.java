package com.jobhub.evidence.domain;

import java.util.List;

/**
 * 项目案例聚合根。最小字段为"使用场景、采取方案、解决问题"，结果可后补。
 * evidenceRefs 为关联证据摘要（只读投影），由 application 层装配。
 */
public class ProjectCase {
	private String id;
	private String title;
	private String scenario;
	private String approach;
	private String problemSolved;
	private String resultText;
	private String createdAt;
	private String updatedAt;
	private String deletedAt;
	private long version;
	private List<Evidence> evidenceRefs = List.of();

	public static ProjectCase create(String id, String title, String scenario, String approach,
			String problemSolved, String resultText, String now) {
		ProjectCase project = new ProjectCase();
		project.id = id;
		project.title = title;
		project.scenario = scenario;
		project.approach = approach;
		project.problemSolved = problemSolved;
		project.resultText = resultText;
		project.createdAt = now;
		project.updatedAt = now;
		return project;
	}

	public void updateMeta(String title, String scenario, String approach, String problemSolved,
			String resultText, String now) {
		this.title = title;
		this.scenario = scenario;
		this.approach = approach;
		this.problemSolved = problemSolved;
		this.resultText = resultText;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public String getTitle() { return title; }
	public String getScenario() { return scenario; }
	public String getApproach() { return approach; }
	public String getProblemSolved() { return problemSolved; }
	public String getResultText() { return resultText; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public String getDeletedAt() { return deletedAt; }
	public long getVersion() { return version; }
	public List<Evidence> getEvidenceRefs() { return evidenceRefs == null ? List.of() : evidenceRefs; }
	public void setEvidenceRefs(List<Evidence> evidenceRefs) {
		this.evidenceRefs = evidenceRefs == null ? List.of() : evidenceRefs;
	}
}
