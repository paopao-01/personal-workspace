package com.jobhub.evidence.domain;

import java.util.List;

/**
 * 证据引用。urlOrPath 为用户填写的外部链接或本地路径，仅作为文本保存；
 * 应用不读取、不扫描、不上传其指向的内容，也不写入日志。
 * skillIds 为关联技能（只读投影），由 application 层装配。
 */
public class Evidence {
	private String id;
	private EvidenceType type;
	private String title;
	private String whereUsed;
	private String problemSolved;
	private String approach;
	private String resultText;
	private String urlOrPath;
	private String createdAt;
	private String updatedAt;
	private String deletedAt;
	private long version;
	private List<String> skillIds = List.of();

	public static Evidence create(String id, EvidenceType type, String title, String whereUsed,
			String problemSolved, String approach, String resultText, String urlOrPath, String now) {
		Evidence evidence = new Evidence();
		evidence.id = id;
		evidence.type = type;
		evidence.title = title;
		evidence.whereUsed = whereUsed;
		evidence.problemSolved = problemSolved;
		evidence.approach = approach;
		evidence.resultText = resultText;
		evidence.urlOrPath = urlOrPath;
		evidence.createdAt = now;
		evidence.updatedAt = now;
		return evidence;
	}

	public void updateMeta(EvidenceType type, String title, String whereUsed, String problemSolved,
			String approach, String resultText, String urlOrPath, String now) {
		this.type = type;
		this.title = title;
		this.whereUsed = whereUsed;
		this.problemSolved = problemSolved;
		this.approach = approach;
		this.resultText = resultText;
		this.urlOrPath = urlOrPath;
		this.updatedAt = now;
	}

	public String getId() { return id; }
	public EvidenceType getType() { return type; }
	public String getTitle() { return title; }
	public String getWhereUsed() { return whereUsed; }
	public String getProblemSolved() { return problemSolved; }
	public String getApproach() { return approach; }
	public String getResultText() { return resultText; }
	public String getUrlOrPath() { return urlOrPath; }
	public String getCreatedAt() { return createdAt; }
	public String getUpdatedAt() { return updatedAt; }
	public String getDeletedAt() { return deletedAt; }
	public long getVersion() { return version; }
	public List<String> getSkillIds() { return skillIds == null ? List.of() : skillIds; }
	public void setSkillIds(List<String> skillIds) {
		this.skillIds = skillIds == null ? List.of() : skillIds;
	}
}
