package com.jobhub.skill.domain;

/**
 * 技能画像投影：skill 主表 + user_skill 三维度（self_level / evidence_status / interview_performance）。
 * 尚无 user_skill 记录时 selfLevel 与 evidenceStatus 为 null（界面显示"未评估"），version 为 0；
 * 三维度相互独立，修改任一字段不得覆盖其余两项（02-state-machines.md §8.1）。
 */
public class SkillProfile {
	private String skillId;
	private String skillName;
	private Integer selfLevel;
	private String evidenceStatus;
	private String userSkillId;
	private long version;

	public String getSkillId() { return skillId; }
	public void setSkillId(String skillId) { this.skillId = skillId; }
	public String getSkillName() { return skillName; }
	public void setSkillName(String skillName) { this.skillName = skillName; }
	public Integer getSelfLevel() { return selfLevel; }
	public void setSelfLevel(Integer selfLevel) { this.selfLevel = selfLevel; }
	public String getEvidenceStatus() { return evidenceStatus; }
	public void setEvidenceStatus(String evidenceStatus) { this.evidenceStatus = evidenceStatus; }
	public String getUserSkillId() { return userSkillId; }
	public void setUserSkillId(String userSkillId) { this.userSkillId = userSkillId; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }
}
