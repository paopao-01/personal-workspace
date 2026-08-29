package com.jobhub.interview.application;

import java.util.ArrayList;
import java.util.List;

public class ProjectCaseSummary {
	private String id;
	private String title;
	private String scenario;
	private String approach;
	private String problemSolved;
	private long version;
	private List<EvidenceReference> evidenceRefs = new ArrayList<>();

	public static ProjectCaseSummary missing() {
		ProjectCaseSummary p = new ProjectCaseSummary();
		p.id = "00000000-0000-0000-0000-000000000000";
		p.title = "待补充项目案例";
		p.scenario = "待补充";
		p.approach = "待补充";
		p.problemSolved = "待补充";
		p.version = 0;
		return p;
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getScenario() { return scenario; }
	public void setScenario(String scenario) { this.scenario = scenario; }
	public String getApproach() { return approach; }
	public void setApproach(String approach) { this.approach = approach; }
	public String getProblemSolved() { return problemSolved; }
	public void setProblemSolved(String problemSolved) { this.problemSolved = problemSolved; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }
	public List<EvidenceReference> getEvidenceRefs() { return evidenceRefs == null ? List.of() : evidenceRefs; }
	public void setEvidenceRefs(List<EvidenceReference> evidenceRefs) {
		this.evidenceRefs = evidenceRefs == null ? new ArrayList<>() : evidenceRefs;
	}
}
